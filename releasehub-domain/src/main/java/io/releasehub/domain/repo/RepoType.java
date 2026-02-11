package io.releasehub.domain.repo;

/**
 * 代码仓库类型
 * <ul>
 *   <li>LIBRARY - 纯功能包，被其他仓库依赖</li>
 *   <li>SERVICE - 服务包，可独立部署</li>
 * </ul>
 */
public enum RepoType {
    LIBRARY,
    SERVICE
}
