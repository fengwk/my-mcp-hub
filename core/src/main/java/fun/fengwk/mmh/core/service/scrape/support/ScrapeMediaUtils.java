package fun.fengwk.mmh.core.service.scrape.support;

import fun.fengwk.convention4j.common.lang.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * Shared media detection helper for scrape runtime.
 *
 * @author fengwk
 */
public final class ScrapeMediaUtils {

    private ScrapeMediaUtils() {
    }

    public static String resolveMime(Map<String, String> headers) {
        String contentType = findHeader(headers, "content-type");
        if (StringUtils.isBlank(contentType)) {
            return "application/octet-stream";
        }

        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int semicolonIndex = normalized.indexOf(';');
        if (semicolonIndex >= 0) {
            normalized = normalized.substring(0, semicolonIndex).trim();
        }
        return StringUtils.isBlank(normalized) ? "application/octet-stream" : normalized;
    }

    public static String findHeader(Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty() || StringUtils.isBlank(name)) {
            return "";
        }

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue() == null ? "" : entry.getValue();
            }
        }
        return "";
    }

    public static boolean isDirectMediaResponse(String mime, String contentDisposition, String url) {
        if (StringUtils.isNotBlank(contentDisposition)
            && contentDisposition.toLowerCase(Locale.ROOT).contains("attachment")) {
            return true;
        }
        if ("application/octet-stream".equals(mime)) {
            return hasMediaLikeFileExtension(url);
        }
        return mime.startsWith("image/")
            || mime.startsWith("video/")
            || mime.startsWith("audio/")
            || "application/pdf".equals(mime);
    }

    public static boolean hasMediaLikeFileExtension(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }

        String lowerUrl = url.toLowerCase(Locale.ROOT);
        int fragmentIndex = lowerUrl.indexOf('#');
        if (fragmentIndex >= 0) {
            lowerUrl = lowerUrl.substring(0, fragmentIndex);
        }
        int queryIndex = lowerUrl.indexOf('?');
        if (queryIndex >= 0) {
            lowerUrl = lowerUrl.substring(0, queryIndex);
        }
        return lowerUrl.endsWith(".png")
            || lowerUrl.endsWith(".jpg")
            || lowerUrl.endsWith(".jpeg")
            || lowerUrl.endsWith(".webp")
            || lowerUrl.endsWith(".gif")
            || lowerUrl.endsWith(".bmp")
            || lowerUrl.endsWith(".svg")
            || lowerUrl.endsWith(".mp4")
            || lowerUrl.endsWith(".mov")
            || lowerUrl.endsWith(".mkv")
            || lowerUrl.endsWith(".webm")
            || lowerUrl.endsWith(".mp3")
            || lowerUrl.endsWith(".wav")
            || lowerUrl.endsWith(".flac")
            || lowerUrl.endsWith(".m4a")
            || lowerUrl.endsWith(".pdf");
    }

}
