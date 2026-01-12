package io.releasehub.infrastructure.version;

import io.releasehub.application.version.VersionUpdateRequest;
import io.releasehub.application.version.VersionUpdateResult;
import io.releasehub.application.version.VersionUpdater;
import io.releasehub.common.exception.BizException;
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
import java.util.List;

/**
 * Maven 版本更新器实现
 * <p>
 * 支持单模块和多模块 Maven 项目的版本更新。
 * 对于多模块项目，更新父 POM 的版本，子模块继承父版本。
 */
@Slf4j
@Component
public class MavenVersionUpdater implements VersionUpdater {

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

            // 读取原始内容
            String originalContent = Files.readString(pomFile);
            
            // 解析并更新版本
            Document doc = parsePom(pomFile.toFile());
            String oldVersion = extractVersion(doc);
            
            if (oldVersion == null) {
                return VersionUpdateResult.failure(
                        "POM 文件中未找到 <version> 元素。请确保 pom.xml 包含版本号定义。",
                        pomPath
                );
            }

            // 更新版本
            updateVersion(doc, request.targetVersion());
            
            // 生成更新后的内容
            String updatedContent = serializeDocument(doc);
            
            // 生成 diff
            String diff = generateDiff(originalContent, updatedContent);
            
            // 写回文件（注意：根据设计，MVP 阶段只做文件改写，不做 Git 操作）
            Files.writeString(pomFile, updatedContent);
            
            log.info("Updated Maven version from {} to {} in {}", oldVersion, request.targetVersion(), pomPath);
            
            return VersionUpdateResult.success(oldVersion, request.targetVersion(), diff, pomPath);
            
        } catch (BizException e) {
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
    private String extractVersion(Document doc) {
        Element root = doc.getDocumentElement();
        NodeList versionNodes = root.getElementsByTagName("version");
        
        if (versionNodes.getLength() == 0) {
            return null;
        }
        
        // 获取第一个 version 元素（通常是项目版本）
        Node versionNode = versionNodes.item(0);
        return versionNode.getTextContent().trim();
    }

    /**
     * 更新版本号
     */
    private void updateVersion(Document doc, String newVersion) {
        Element root = doc.getDocumentElement();
        NodeList versionNodes = root.getElementsByTagName("version");
        
        if (versionNodes.getLength() == 0) {
            throw new BizException("VERSION_NOT_FOUND", "Version element not found in POM");
        }
        
        // 更新第一个 version 元素（项目版本）
        Node versionNode = versionNodes.item(0);
        versionNode.setTextContent(newVersion);
        
        // 注意：多模块场景下，子模块通常继承父版本，不需要单独更新
        // 如果子模块有显式 version，这里不处理（根据设计文档的策略）
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
