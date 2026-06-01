package com.ruoyi.jwmap.mapper;

import com.ruoyi.jwmap.domain.JwPeerBankInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 同业银行信息Mapper接口
 */
@Mapper
public interface JwPeerBankInfoMapper {

    List<JwPeerBankInfo> selectJwPeerBankInfoList(JwPeerBankInfo peerBank);

    JwPeerBankInfo selectJwPeerBankInfoById(Long peerBankId);

    List<JwPeerBankInfo> selectByCity(@Param("city") String city);

    int insertJwPeerBankInfo(JwPeerBankInfo peerBank);

    int upsertJwPeerBankInfo(JwPeerBankInfo peerBank);

    int deleteJwPeerBankInfoById(Long peerBankId);
}
