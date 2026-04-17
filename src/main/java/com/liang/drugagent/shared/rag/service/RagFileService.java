package com.liang.drugagent.shared.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.liang.drugagent.shared.rag.entity.RagFile;
import com.liang.drugagent.shared.rag.mapper.RagFileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * RAG 文件元数据服务。
 *
 * <p>负责 rag_file 表的 CRUD 操作，管理 OSS 文件与向量库 sourceId 的关联。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagFileService {

    private final RagFileMapper ragFileMapper;

    /**
     * 保存 RAG 文件记录。
     */
    public void save(RagFile ragFile) {
        ragFileMapper.insert(ragFile);
        log.info("[RagFileService] 文件记录已保存 - ossId={}, sourceId={}",
                ragFile.getOssId(), ragFile.getSourceId());
    }

    /**
     * 根据 ID 查询文件记录。
     */
    public Optional<RagFile> findById(Long id) {
        RagFile file = ragFileMapper.selectById(id);
        return Optional.ofNullable(file);
    }

    /**
     * 根据 OSS ID 查询文件记录。
     */
    public Optional<RagFile> findByOssId(String ossId) {
        return ragFileMapper.findByOssId(ossId);
    }

    /**
     * 根据 sourceId 查询文件记录。
     */
    public Optional<RagFile> findBySourceId(String sourceId) {
        return ragFileMapper.findBySourceId(sourceId);
    }

    /**
     * 根据机构 ID 查询所有未删除的文件记录。
     *
     * <p>只返回有效且未删除的记录，按创建时间倒序。</p>
     */
    public List<RagFile> findByOrgId(String orgId) {
        LambdaQueryWrapper<RagFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(orgId != null, RagFile::getOrgId, orgId)
               .eq(RagFile::getDeleted, 0)
               .eq(RagFile::getStatus, 1)
               .orderByDesc(RagFile::getCreatedAt);
        return ragFileMapper.selectList(wrapper);
    }

    /**
     * 根据机构 ID 和 scene 查询文件记录。
     */
    public List<RagFile> findByOrgIdAndScene(String orgId, String scene) {
        LambdaQueryWrapper<RagFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(orgId != null, RagFile::getOrgId, orgId)
               .eq(RagFile::getDeleted, 0)
               .eq(RagFile::getStatus, 1)
               .eq(scene != null, RagFile::getScene, scene)
               .orderByDesc(RagFile::getCreatedAt);
        return ragFileMapper.selectList(wrapper);
    }

    /**
     * 根据文件名关键字搜索文件。
     *
     * <p>匹配 original_name 或 stored_name。</p>
     */
    public List<RagFile> searchByFileName(String orgId, String keyword) {
        LambdaQueryWrapper<RagFile> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(orgId != null, RagFile::getOrgId, orgId)
               .eq(RagFile::getDeleted, 0)
               .eq(RagFile::getStatus, 1)
               .and(keyword != null && !keyword.isBlank(), w -> w
                       .like(RagFile::getOriginalName, keyword)
                       .or()
                       .like(RagFile::getStoredName, keyword))
               .orderByDesc(RagFile::getCreatedAt);
        return ragFileMapper.selectList(wrapper);
    }

    /**
     * 删除文件记录（软删除）。
     *
     * <p>将 deleted 标记设为 1，status 设为 2。</p>
     */
    @Transactional
    public void delete(Long id) {
        RagFile file = ragFileMapper.selectById(id);
        if (file != null) {
            file.setDeleted(1);
            file.setStatus(2);
            ragFileMapper.updateById(file);
            log.info("[RagFileService] 文件记录已软删除 - id={}, sourceId={}", id, file.getSourceId());
        }
    }

    /**
     * 删除文件记录（软删除），根据 ossId。
     */
    @Transactional
    public void deleteByOssId(String ossId) {
        Optional<RagFile> fileOpt = ragFileMapper.findByOssId(ossId);
        if (fileOpt.isPresent()) {
            delete(fileOpt.get().getId());
        } else {
            log.warn("[RagFileService] 未找到文件记录 - ossId={}", ossId);
        }
    }

    /**
     * 更新文件状态。
     */
    public void updateStatus(Long id, Integer status) {
        LambdaUpdateWrapper<RagFile> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(RagFile::getId, id)
               .set(RagFile::getStatus, status);
        ragFileMapper.update(null, wrapper);
        log.info("[RagFileService] 文件状态已更新 - id={}, status={}", id, status);
    }

    /**
     * 更新 chunk 数量。
     */
    public void updateChunkCount(Long id, Integer chunkCount) {
        LambdaUpdateWrapper<RagFile> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(RagFile::getId, id)
               .set(RagFile::getChunkCount, chunkCount);
        ragFileMapper.update(null, wrapper);
        log.info("[RagFileService] 文件 chunk 数量已更新 - id={}, chunkCount={}", id, chunkCount);
    }

    /**
     * 检查文件是否已入库（幂等检查）。
     */
    public boolean isIngested(String ossId) {
        Optional<RagFile> fileOpt = ragFileMapper.findByOssId(ossId);
        return fileOpt.isPresent() && fileOpt.get().getDeleted() == 0;
    }
}
