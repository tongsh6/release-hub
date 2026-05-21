package io.releasehub.domain.repo;

import io.releasehub.common.exception.ValidationException;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record CloneUrl(String value, String canonicalKey) {
    private static final Pattern SCP_LIKE = Pattern.compile("^git@([^:]+):(.+)$");

    public static CloneUrl parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw ValidationException.repoUrlRequired();
        }
        String value = rawUrl.trim();
        if (value.length() > 512) {
            throw ValidationException.repoUrlTooLong(512);
        }

        Matcher scpLike = SCP_LIKE.matcher(value);
        if (scpLike.matches()) {
            return new CloneUrl(value, canonicalize(scpLike.group(1), scpLike.group(2)));
        }

        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();
            if (scheme == null || path == null || path.isBlank()) {
                throw ValidationException.repoUrlInvalid();
            }
            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!("http".equals(normalizedScheme) || "https".equals(normalizedScheme) || "ssh".equals(normalizedScheme))) {
                throw ValidationException.repoUrlInvalid();
            }
            if (host == null || host.isBlank()) {
                throw ValidationException.repoUrlInvalid();
            }
            return new CloneUrl(value, canonicalize(host, path));
        } catch (IllegalArgumentException e) {
            throw ValidationException.repoUrlInvalid();
        }
    }

    private static String canonicalize(String host, String path) {
        String normalizedHost = host.trim().toLowerCase(Locale.ROOT);
        String normalizedPath = path.trim();
        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        while (normalizedPath.endsWith("/")) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
        }
        if (normalizedPath.endsWith(".git")) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 4);
        }
        if (normalizedHost.isBlank() || normalizedPath.isBlank()) {
            throw ValidationException.repoUrlInvalid();
        }
        return normalizedHost + "/" + normalizedPath.toLowerCase(Locale.ROOT);
    }
}
