package io.releasehub.infrastructure.gitlab;

import io.releasehub.application.port.out.GitLabFilePort;
import io.releasehub.application.settings.SettingsPort;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 真实 GitLab 文件读取适配器，通过 GitLab API v4 读取仓库文件内容。
 *
 * <p>激活条件：设置 {@code releasehub.gitlab.real-file-adapter=true}。
 * 默认使用 {@link MockGitLabFileAdapter}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "releasehub.gitlab.real-file-adapter", havingValue = "true")
public class RealGitLabFileAdapter implements GitLabFilePort {

    private final SettingsPort settingsPort;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public Optional<String> readFile(String repoCloneUrl, String branch, String filePath) {
        final RepoRef ref = parseRepoRef(repoCloneUrl);
        final String token = getToken();

        String encodedFilePath = urlEncode(filePath);
        String encodedRef = urlEncode(branch);
        URI uri = URI.create(String.format("%s/api/v4/projects/%s/repository/files/%s?ref=%s",
                ref.baseUrl, ref.encodedPath, encodedFilePath, encodedRef));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("PRIVATE-TOKEN", token);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});

            Map<String, Object> body = response.getBody();
            if (body == null) {
                log.warn("GitLab API returned empty body for file {} in repo {} branch {}",
                        filePath, repoCloneUrl, branch);
                return Optional.empty();
            }

            String content = (String) body.get("content");
            String encoding = (String) body.get("encoding");

            if (content == null) {
                return Optional.empty();
            }

            if ("base64".equals(encoding)) {
                String decoded = new String(Base64.getDecoder().decode(
                        content.replaceAll("\\s", "")), StandardCharsets.UTF_8);
                return Optional.of(decoded);
            }

            return Optional.of(content);

        } catch (HttpClientErrorException.NotFound e) {
            log.debug("File not found: {} in repo {} branch {}", filePath, repoCloneUrl, branch);
            return Optional.empty();
        } catch (HttpClientErrorException.Forbidden e) {
            log.warn("Permission denied reading file {} in repo {} branch {}: {}",
                    filePath, repoCloneUrl, branch, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error reading file {} in repo {} branch {}: {}",
                    filePath, repoCloneUrl, branch, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Override
    public boolean fileExists(String repoCloneUrl, String branch, String filePath) {
        final RepoRef ref = parseRepoRef(repoCloneUrl);
        final String token = getToken();

        String encodedFilePath = urlEncode(filePath);
        String encodedRef = urlEncode(branch);
        URI uri = URI.create(String.format("%s/api/v4/projects/%s/repository/files/%s?ref=%s",
                ref.baseUrl, ref.encodedPath, encodedFilePath, encodedRef));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("PRIVATE-TOKEN", token);

            restTemplate.exchange(uri, HttpMethod.HEAD, new HttpEntity<>(headers), Void.class);
            return true;

        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (HttpClientErrorException.Forbidden e) {
            log.warn("Permission denied checking file {} in repo {} branch {}",
                    filePath, repoCloneUrl, branch);
            return false;
        } catch (Exception e) {
            log.warn("Error checking file existence {} in repo {} branch {}: {}",
                    filePath, repoCloneUrl, branch, e.getMessage());
            return false;
        }
    }

    private String getToken() {
        var settings = settingsPort.getGitLab();
        if (settings.isEmpty()) {
            throw BusinessException.gitlabSettingsMissing();
        }
        return settings.get().tokenMasked();
    }

    private RepoRef parseRepoRef(String cloneUrl) {
        if (cloneUrl == null || cloneUrl.isBlank()) {
            throw ValidationException.invalidParameter("cloneUrl");
        }
        String trimmed = cloneUrl.trim();
        Matcher ssh = Pattern.compile("^git@([^:]+):(.+?)(\\.git)?$").matcher(trimmed);
        if (ssh.find()) {
            String baseUrl = "https://" + ssh.group(1);
            String path = ssh.group(2);
            return new RepoRef(baseUrl, urlEncode(path));
        }
        Matcher https = Pattern.compile("^(https?)://([^/]+)/(.+?)(\\.git)?$").matcher(trimmed);
        if (https.find()) {
            String baseUrl = https.group(1) + "://" + https.group(2);
            String path = https.group(3);
            return new RepoRef(baseUrl, urlEncode(path));
        }
        throw ValidationException.invalidParameter("cloneUrl");
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record RepoRef(String baseUrl, String encodedPath) {
    }
}
