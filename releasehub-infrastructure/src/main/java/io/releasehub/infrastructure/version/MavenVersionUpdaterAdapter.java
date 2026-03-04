package io.releasehub.infrastructure.version;

import io.releasehub.application.version.VersionUpdateRequest;
import io.releasehub.application.version.VersionUpdateResult;
import io.releasehub.application.version.VersionUpdaterPort;
import io.releasehub.common.exception.BaseException;
import io.releasehub.common.exception.BusinessException;
import io.releasehub.domain.version.BuildTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.StringWriter;
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
public class MavenVersionUpdaterAdapter implements VersionUpdaterPort {

    @Override
    public VersionUpdateResult update(VersionUpdateRequest request) {
        try {
            String pomPath = request.pomPath() != null 
                    ? request.pomPath() 
                    : Paths.get(request.repoPath(), "pom.xml").toString();
            
            Path pomFile = Paths.get(pomPath);
            if (!Files.exists(pomFile)) {
                return VersionUpdateResult.failure(
                        String.format("POM 文件不存在: %s。请检查文件路径是否正确。", pomPath),
                        pomPath
                );
            }

            Document rootDoc = parsePom(pomFile.toFile());
            String oldVersion = extractProjectVersion(rootDoc);

            if (oldVersion == null) {
                return VersionUpdateResult.failure(
                        "POM 文件中未找到 <version> 元素。请确保 pom.xml 包含版本号定义。",
                        pomPath
                );
            }

            String rootGroupId = extractProjectGroupId(rootDoc);
            String rootArtifactId = extractProjectArtifactId(rootDoc);
            List<Path> pomFiles = collectModulePomFiles(pomFile);
            StringBuilder combinedDiff = new StringBuilder();

            for (Path currentPom : pomFiles) {
                String originalContent = Files.readString(currentPom);
                Document currentDoc = parsePom(currentPom.toFile());

                boolean changed = applyVersionUpdate(
                        currentDoc,
                        currentPom.equals(pomFile),
                        rootGroupId,
                        rootArtifactId,
                        oldVersion,
                        request.targetVersion()
                );

                if (!changed) {
                    continue;
                }

                String updatedContent = serializeDocument(currentDoc);
                Files.writeString(currentPom, updatedContent);

                String fileDiff = generateDiff(originalContent, updatedContent);
                if (!fileDiff.isBlank()) {
                    combinedDiff.append("## ")
                            .append(pomFile.getParent().relativize(currentPom))
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

    @Override
    public boolean supports(BuildTool buildTool) {
        return buildTool == BuildTool.MAVEN;
    }

    /**
     * 解析 POM 文件
     */
    private Document parsePom(File pomFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(pomFile);
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

    private List<Path> collectModulePomFiles(Path rootPomPath) throws Exception {
        List<Path> pomFiles = new ArrayList<>();
        ArrayDeque<Path> queue = new ArrayDeque<>();
        Set<Path> visited = new HashSet<>();

        queue.add(rootPomPath.toAbsolutePath().normalize());

        while (!queue.isEmpty()) {
            Path currentPom = queue.removeFirst();
            if (!visited.add(currentPom)) {
                continue;
            }

            pomFiles.add(currentPom);
            Document doc = parsePom(currentPom.toFile());
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
                Path modulePom = currentPom.getParent().resolve(moduleName).resolve("pom.xml").normalize();
                if (!Files.exists(modulePom)) {
                    throw new IllegalStateException("Module POM not found: " + modulePom);
                }
                queue.add(modulePom);
            }
        }

        return pomFiles;
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
