package com.nineone.markdown.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nineone.markdown.entity.BackupRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 备份记录 Mapper 接口
 */
@Mapper
public interface BackupRecordMapper extends BaseMapper<BackupRecord> {
}
