package com.zkxpicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.zkxpicturebackend.exception.BusinessException;
import com.zkxpicturebackend.exception.ErrorCode;
import com.zkxpicturebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class UrlPictureUpload extends PictureUploadTemplate {

    // 允许的图片 Content-Type
    private static final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
    // 最大下载大小：10MB（与 application.yml 的 multipart 上限保持一致）
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;

    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址不能为空");
        try {
            // 1. 验证 URL 格式
            URL url = new URL(fileUrl);
            // 2. 校验 URL 协议
            String protocol = url.getProtocol();
            ThrowUtils.throwIf(!("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol)),
                    ErrorCode.PARAMS_ERROR, "仅支持 HTTP 或 HTTPS 协议的文件地址");
            // 3. SSRF 防护：校验域名解析出的 IP 不允许为内网/保留地址
            checkSsrFRisk(url);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式不正确");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址无法解析");
        }

        // 4. 发送 HEAD 请求以验证文件是否存在
        HttpResponse response = null;
        try {
            response = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
            // 未正常返回，无需执行其他判断
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                return;
            }
            // 5. 校验文件类型
            String contentType = response.header("Content-Type");
            if (StrUtil.isNotBlank(contentType)) {
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType.toLowerCase()),
                        ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
            // 6. 校验文件大小
            String contentLengthStr = response.header("Content-Length");
            if (StrUtil.isNotBlank(contentLengthStr)) {
                try {
                    long contentLength = Long.parseLong(contentLengthStr);
                    ThrowUtils.throwIf(contentLength > MAX_FILE_SIZE, ErrorCode.PARAMS_ERROR, "文件大小不能超过 10M");
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小格式错误");
                }
            }
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * SSRF 防护：解析域名对应的所有 IP，拒绝内网/保留/云元数据地址
     * 注意：需在下载前调用，避免服务端访问内网资源
     */
    private void checkSsrFRisk(URL url) throws IOException {
        String host = url.getHost();
        if (StrUtil.isBlank(host)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址无效");
        }
        // IPv6 字面量（如 [::1]）会带中括号，需要去除
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses) {
            if (isInternalAddress(address)) {
                log.warn("检测到 SSRF 风险地址: host={}, ip={}", host, address.getHostAddress());
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不允许访问内网或保留地址");
            }
        }
    }

    /**
     * 判断是否为内网/保留/云元数据地址
     */
    private boolean isInternalAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet4Address) {
            byte[] addr = address.getAddress();
            int first = addr[0] & 0xFF;
            int second = addr[1] & 0xFF;
            // 169.254.0.0/16（云元数据/链路本地）、100.64.0.0/10（运营商级 NAT）、224.0.0.0/4（组播）、240.0.0.0/4（保留）
            if (first == 169 && second == 254) {
                return true;
            }
            if (first == 100 && (second & 0xC0) == 64) {
                return true;
            }
            if (first >= 224) {
                return true;
            }
        }
        if (address instanceof Inet6Address) {
            byte[] addr = address.getAddress();
            int firstByte = addr[0] & 0xFF;
            // fc00::/7（ULA）、fe80::/10（链路本地，isLinkLocalAddress 已覆盖）、2001:db8::/32（文档示例）
            if ((firstByte & 0xFE) == 0xFC) {
                return true;
            }
            if (firstByte == 0x20 && (addr[1] & 0xFF) == 0x01 && (addr[2] & 0xFF) == 0x0D && (addr[3] & 0xFF) == 0xB8) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected String getOriginFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        try {
            URL url = new URL(fileUrl);
            String path = url.getPath();
            String fullFileName = path.substring(path.lastIndexOf('/') + 1);
            if (fullFileName.isEmpty() || !fullFileName.contains(".")) {
                // 没有扩展名时，尝试从 URL 参数或默认值补充
                // 简单处理：默认 .jpg（实际可更智能，如根据 Content-Type）
                fullFileName = fullFileName.isEmpty() ? "image.jpg" : fullFileName + ".jpg";
            }
            return fullFileName;
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的图片URL");
        }
    }

    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        String fileUrl = (String) inputSource;
        // 流式下载并限制大小，防止下载超大文件耗尽磁盘/内存
        HttpResponse response = null;
        try {
            response = HttpUtil.createRequest(Method.GET, fileUrl).execute();
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件下载失败");
            }
            try (InputStream inputStream = response.bodyStream();
                 FileOutputStream outputStream = new FileOutputStream(file)) {
                long totalBytes = 0;
                byte[] buffer = new byte[8192];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    totalBytes += len;
                    if (totalBytes > MAX_FILE_SIZE) {
                        throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 10M");
                    }
                    outputStream.write(buffer, 0, len);
                }
                outputStream.flush();
            }
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }
}
