package io.releasehub.infrastructure.version;

import io.releasehub.application.port.out.GitLabFilePort;
import io.releasehub.application.repo.CodeRepositoryPort;
import io.releasehub.application.version.VersionUpdateRequest;
import io.releasehub.application.version.VersionUpdateResult;
import io.releasehub.application.version.VersionUpdaterPort;
import io.releasehub.common.exception.BaseException;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.domain.repo.CodeRepository;
import io.releasehub.domain.version.BuildTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Maven 版本更新器实现
 * <p>
 * 支持单模块和多模块 Maven 项目的版本更新。
 * 对于多模块项目，更新父 POM 的版本，子模块继承父版本。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MavenVersionUpdaterAdapter implements VersionUpdaterPort {

    private final GitLabFilePort gitLabFilePort;
    private final CodeRepositoryPort codeRepositoryPort;

    @Override
    public VersionUpdateResult update(VersionUpdateRequest request) {
        try {
            FileOperator operator = getOperator(request);
            String pomPath = request.pomPath() != null
                    ? request.pomPath()
                    : (request.repoPath() != null && !request.repoPath().equals(".") 
                        ? Paths.get(request.repoPath(), "pom.xml").toString()
                        : "pom.xml");

            if (!operator.exists(pomPath)) {
                return VersionUpdateResult.failure(
                        String.format("POM 文件不存在: %s。请检查文件路径是否正确。", pomPath),
                        pomPath
                );
            }

            Document rootDoc = parsePom(operator.read(pomPath));
            String oldVersion = extractProjectVersion(rootDoc);

            if (oldVersion == null) {
                return VersionUpdateResult.failure(
                        "POM 文件中未找到 <version> 元素。请确保 pom.xml 包含版本号定义。",
                        pomPath
                );
            }

            String rootGroupId = extractProjectGroupId(rootDoc);
            String rootArtifactId = extractProjectArtifactId(rootDoc);
            List<String> pomFiles = collectModulePomFiles(operator, pomPath);
            StringBuilder combinedDiff = new StringBuilder();

            for (String currentPom : pomFiles) {
                String originalContent = operator.read(currentPom);
                Document currentDoc = parsePom(originalContent);

                boolean changed = applyVersionUpdate(
                        currentDoc,
                        currentPom.equals(pomPath),
                        rootGroupId,
                        rootArtifactId,
                        oldVersion,
                        request.targetVersion()
                );

                if (!changed) {
                    continue;
                }

                String updatedContent = serializeDocument(currentDoc);
                operator.write(currentPom, updatedContent);

                String fileDiff = generateDiff(originalContent, updatedContent);
                if (!fileDiff.isBlank()) {
                    combinedDiff.append("## ")
                            .append(currentPom)
                            .append("\n")
                            .append(fileDiff)
                            .append("\n");
                }
            }

            log.info("Updated Maven version from {} to {} in {}", oldVersion, request.targetVersion(), pomPath);

            return VersionUpdateResult.success(oldVersion, request.targetVersion(), combinedDiff.toString(), pomPath);

        } catch (BaseException e) {
            return VersionUpdateResult.failure(e.getMessage(), request.pomPath());
        } catch (Exception e) {
            log.error("Failed to update Maven version", e);
            String errorMsg = String.format(
                    "版本更新失败: %s。请检查 POM 文件格式是否正确，或联系管理员。",
                    e.getMessage()
            );
            return VersionUpdateResult.failure(errorMsg, request.pomPath());
        }
    }

    private FileOperator getOperator(VersionUpdateRequest request) {
        if (request.branchName() != null) {
            CodeRepository repo = codeRepositoryPort.findById(request.repoId()).orElse(null);
            if (repo != null && repo.getCloneUrl() != null) {
                log.info("Using RemoteFileOperator for repo {} on branch {}", repo.getName(), request.branchName());
                return new RemoteFileOperator(gitLabFilePort, repo.getCloneUrl(), request.branchName());
            }
        }
        log.info("Using LocalFileOperator for repoPath {}", request.repoPath());
        return new LocalFileOperator();
    }

    private interface FileOperator {
        String read(String path) throws Exception;
        void write(String path, String content) throws Exception;
        boolean exists(String path);
    }

    @RequiredArgsConstructor
    private static class RemoteFileOperator implements FileOperator {
        private final GitLabFilePort gitLabFilePort;
        private final String repoCloneUrl;
        private final String branch;

        @Override
        public String read(String path) {
            return gitLabFilePort.readFile(repoCloneUrl, branch, path)
                    .orElseThrow(() -> new RuntimeException("File not found on GitLab: " + path));
        }

        @Override
        public void write(String path, String content) {
            String commitMessage = String.format("ReleaseHub: Update %s version to align with release window", path);
            boolean ok = gitLabFilePort.updateFile(repoCloneUrl, branch, path, content, commitMessage);
            if (!ok) {
                throw new RuntimeException("Failed to update file on GitLab: " + path);
            }
        }

        @Override
        public boolean exists(String path) {
            return gitLabFilePort.fileExists(repoCloneUrl, branch, path);
        }
    }

    private static class LocalFileOperator implements FileOperator {
        @Override
        public String read(String path) throws Exception {
            return Files.readString(Paths.get(path));
        }

        @Override
        public void write(String path, String content) throws Exception {
            Files.writeString(Paths.get(path), content);
        }

        @Override
        public boolean exists(String path) {
            return Files.exists(Paths.get(path));
        }
    }

    @Override
    public boolean supports(BuildTool buildTool) {
        return buildTool == BuildTool.MAVEN;
    }

    /**
     * 解析 POM 文件
     */
    private Document parsePom(String content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))));
    }

    /**
     * 解析 POM 文件 (保留旧方法签名兼容性，如果内部还在用)
     */
    private Document parsePom(File pomFile) throws Exception {
        return parsePom(Files.readString(pomFile.toPath()));
    }

    /**
     * 提取当前版本号
     */
    private String extractProjectVersion(Document doc) {
        Element root = doc.getDocumentElement();
        Element versionElement = getDirectChildElement(root, "version");
        if (versionElement == null) {
            return null;
        }
        return versionElement.getTextContent().trim();
    }

    private String extractProjectGroupId(Document doc) {
        Element root = doc.getDocumentElement();
        Element groupIdElement = getDirectChildElement(root, "groupId");
        if (groupIdElement != null) {
            return groupIdElement.getTextContent().trim();
        }
        Element parentElement = getDirectChildElement(root, "parent");
        return parentElement == null ? null : getDirectChildText(parentElement, "groupId");
    }

    private String extractProjectArtifactId(Document doc) {
        Element root = doc.getDocumentElement();
        return getDirectChildText(root, "artifactId");
    }

    private List<String> collectModulePomFiles(FileOperator operator, String rootPomPath) throws Exception {
        List<String> pomFiles = new ArrayList<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        queue.add(rootPomPath);

        while (!queue.isEmpty()) {
            String currentPom = queue.removeFirst();
            if (!visited.add(currentPom)) {
                continue;
            }

            pomFiles.add(currentPom);
            Document doc = parsePom(operator.read(currentPom));
            Element root = doc.getDocumentElement();
            Element modulesElement = getDirectChildElement(root, "modules");
            if (modulesElement == null) {
                continue;
            }

            NodeList children = modulesElement.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                String localName = child.getLocalName() != null ? child.getLocalName() : child.getNodeName();
                if (!"module".equals(localName)) {
                    continue;
                }
                String moduleName = child.getTextContent().trim();
                if (moduleName.isEmpty()) {
                    continue;
                }

                // 鲁棒的路径处理: resolve modulePom relative to currentPom
                String modulePom;
                Path currentPath = Paths.get(currentPom);
                Path parentPath = currentPath.getParent();
                if (parentPath != null) {
                    modulePom = parentPath.resolve(moduleName).resolve("pom.xml").toString();
                } else {
                    modulePom = Paths.get(moduleName, "pom.xml").toString();
                }

                // 规范化路径 (处理 ./ or ../ 并统一使用正斜杠)
                modulePom = normalizePath(modulePom);

                if (!operator.exists(modulePom)) {
                    log.warn("Module POM not found: {}", modulePom);
                    continue;
                }
                queue.add(modulePom);
            }
        }

        return pomFiles;
    }

    private String normalizePath(String path) {
        // 使用 Paths 规范化 String 路径
        return Paths.get(path).normalize().toString().replace("\\", "/");
    }

    private boolean applyVersionUpdate(
            Document doc,
            boolean isRootPom,
            String rootGroupId,
            String rootArtifactId,
            String oldVersion,
            String newVersion
    ) {
        Element root = doc.getDocumentElement();
        boolean changed = false;

        if (isRootPom) {
            if (!setDirectChildText(root, "version", newVersion)) {
                throw BusinessException.versionNotFoundInFile();
            }
            return true;
        }

        Element parentElement = getDirectChildElement(root, "parent");
        if (parentElement != null) {
            String parentGroupId = getDirectChildText(parentElement, "groupId");
            String parentArtifactId = getDirectChildText(parentElement, "artifactId");
            if (rootGroupId != null
                    && rootArtifactId != null
                    && rootGroupId.equals(parentGroupId)
                    && rootArtifactId.equals(parentArtifactId)) {
                changed |= setDirectChildText(parentElement, "version", newVersion);
            }
        }

        Element projectVersionElement = getDirectChildElement(root, "version");
        if (projectVersionElement != null && oldVersion.equals(projectVersionElement.getTextContent().trim())) {
            projectVersionElement.setTextContent(newVersion);
            changed = true;
        }

        return changed;
    }

    private Element getDirectChildElement(Element parent, String childName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String localName = child.getLocalName() != null ? child.getLocalName() : child.getNodeName();
            if (childName.equals(localName)) {
                return (Element) child;
            }
        }
        return null;
    }

    private String getDirectChildText(Element parent, String childName) {
        Element child = getDirectChildElement(parent, childName);
        return child == null ? null : child.getTextContent().trim();
    }

    private boolean setDirectChildText(Element parent, String childName, String value) {
        Element child = getDirectChildElement(parent, childName);
        if (child == null) {
            return false;
        }
        if (value.equals(child.getTextContent().trim())) {
            return false;
        }
        child.setTextContent(value);
        return true;
    }

    /**
     * 序列化 Document 为字符串
     */
    private String serializeDocument(Document doc) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    /**
     * 生成简单的 diff（行级别对比）
     */
    private String generateDiff(String oldContent, String newContent) {
        List<String> oldLines = List.of(oldContent.split("\n"));
        List<String> newLines = List.of(newContent.split("\n"));
        
        StringBuilder diff = new StringBuilder();
        int maxLines = Math.max(oldLines.size(), newLines.size());
        
        for (int i = 0; i < maxLines; i++) {
            String oldLine = i < oldLines.size() ? oldLines.get(i) : null;
            String newLine = i < newLines.size() ? newLines.get(i) : null;
            
            if (oldLine != null && newLine != null) {
                if (!oldLine.equals(newLine)) {
                    diff.append(String.format("@@ -%d +%d @@\n", i + 1, i + 1));
                    diff.append("-").append(oldLine).append("\n");
                    diff.append("+").append(newLine).append("\n");
                }
            } else if (oldLine != null) {
                diff.append(String.format("@@ -%d +%d @@\n", i + 1, i));
                diff.append("-").append(oldLine).append("\n");
            } else if (newLine != null) {
                diff.append(String.format("@@ -%d +%d @@\n", i, i + 1));
                diff.append("+").append(newLine).append("\n");
            }
        }
        
        return diff.toString();
    }
}
