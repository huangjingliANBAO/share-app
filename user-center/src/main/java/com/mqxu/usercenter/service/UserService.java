package com.mqxu.usercenter.service;

import com.mqxu.usercenter.dao.BonusEventLogMapper;
import com.mqxu.usercenter.dao.UserMapper;
import com.mqxu.usercenter.domain.dto.LoginDTO;
import com.mqxu.usercenter.domain.dto.ResponseDTO;
import com.mqxu.usercenter.domain.dto.UserAddBonusMsgDTO;
import com.mqxu.usercenter.domain.dto.UserSignInDTO;
import com.mqxu.usercenter.domain.entity.BonusEventLog;
import com.mqxu.usercenter.domain.entity.User;
import com.mqxu.usercenter.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;

import java.util.Date;
import java.util.List;

/**
 * @description:
 * @author: mqxu
 * @create: 2020-10-3
 **/
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class UserService {
    private final UserMapper userMapper;
    private final BonusEventLogMapper bonusEventLogMapper;

    public User findById(Integer id) {
        return this.userMapper.selectByPrimaryKey(id);
    }

    public User login(LoginDTO loginDTO, String openId) {
        //先根据openId查找用户
        Example example = new Example(User.class);
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("wxId", openId);
        List<User> users = this.userMapper.selectByExample(example);
        //没找到，是新用户，直接注册
        if (users.size() == 0) {
            User saveUser = User.builder()
                    .wxId(openId)
                    .avatarUrl(loginDTO.getAvatarUrl())
                    .wxNickname(loginDTO.getWxNickname())
                    .roles("user")
                    .bonus(100)
                    .createTime(new Date())
                    .updateTime(new Date())
                    .build();
            this.userMapper.insertSelective(saveUser);
            return saveUser;
        }
        return users.get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateBonus(UserAddBonusMsgDTO userAddBonusMsgDTO) {
        System.out.println(userAddBonusMsgDTO);
        // 1. 为用户修改积分
        Integer userId = userAddBonusMsgDTO.getUserId();
        Integer bonus = userAddBonusMsgDTO.getBonus();
        User user = this.userMapper.selectByPrimaryKey(userId);
        user.setBonus(user.getBonus() + bonus);
        this.userMapper.updateByPrimaryKeySelective(user);

        // 2. 记录日志到bonus_event_log表里面
        this.bonusEventLogMapper.insert(
                BonusEventLog.builder()
                        .userId(userId)
                        .value(bonus)
                        .event(userAddBonusMsgDTO.getEvent())
                        .createTime(new Date())
                        .description(userAddBonusMsgDTO.getDescription())
                        .build()
        );
        log.info("积分添加完毕...");
    }

    public List<BonusEventLog> getBonusEventLogs(int userId) {
        Example example = new Example(BonusEventLog.class);
        example.setOrderByClause("id DESC");
        Example.Criteria criteria = example.createCriteria();
        criteria.andEqualTo("userId", userId);
        return bonusEventLogMapper.selectByExample(example);
    }

    public ResponseDTO signIn(UserSignInDTO signInDTO) {
        User user = this.userMapper.selectByPrimaryKey(signInDTO.getUserId());
        if (user == null){
            throw new IllegalArgumentException("该用户不存在!");
        }
        Example example = new Example(BonusEventLog.class);
        Example.Criteria criteria = example.createCriteria();
        example.setOrderByClause("id DESC");
        criteria.andEqualTo("userId",signInDTO.getUserId());
        criteria.andEqualTo("event","SIGN_IN");
        List<BonusEventLog> bonusEventLog = this.bonusEventLogMapper.selectByExample(example);
        //判断日志表有没有记录，如果没有直接插入数据并提示签到成功
        if (bonusEventLog.size() == 0){
            this.bonusEventLogMapper.insert(BonusEventLog.builder()
                    .userId(signInDTO.getUserId())
                    .event("SIGN_IN")
                    .value(20)
                    .description("签到加积分")
                    .createTime(new Date())
                    .build());
            user.setBonus(user.getBonus()+20);
            this.userMapper.updateByPrimaryKeySelective(user);
            return new ResponseDTO(true,"200","签到成功",user,1l);
        }else {
            BonusEventLog bonusEventLog1 = bonusEventLog.get(0);
            Date date = bonusEventLog1.getCreateTime();
            try {
                if (DateUtil.checkAllotSigin(date) == 0){
                    this.bonusEventLogMapper.insert(BonusEventLog.builder()
                            .userId(signInDTO.getUserId())
                            .event("SIGN_IN")
                            .value(20)
                            .description("签到加积分")
                            .createTime(new Date())
                            .build());
                    user.setBonus(user.getBonus()+20);
                    this.userMapper.updateByPrimaryKeySelective(user);
                    return new ResponseDTO(true,"200","签到成功",user,1l);
                }
                else if (DateUtil.checkAllotSigin(date) == 1){
                    return new ResponseDTO(false,"201","签到失败",user.getWxNickname()+"今天已经签到过了",1l);
                }
                else if (DateUtil.checkAllotSigin(date) == 2){
                    return new ResponseDTO(false,"202","签到失败",user.getWxNickname()+"用户，今天数据错乱了",1l);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return new ResponseDTO(true,"200","签到成功",user.getWxNickname()+"签到成功",1l);
        }
    }

    public  ResponseDTO checkIsSign(UserSignInDTO signInDTO){
        User user = this.userMapper.selectByPrimaryKey(signInDTO.getUserId());
        if (user == null){
            throw new IllegalArgumentException("该用户不存在!");
        }
        Example example = new Example(BonusEventLog.class);
        Example.Criteria criteria = example.createCriteria();
        example.setOrderByClause("id DESC");
        criteria.andEqualTo("userId",signInDTO.getUserId());
        criteria.andEqualTo("event","SIGN_IN");
        List<BonusEventLog> bonusEventLog = this.bonusEventLogMapper.selectByExample(example);
        if (bonusEventLog.size() == 0){
            return new ResponseDTO(true,"200","该用户还没有签到","可以签到",1l);
        }else {
            BonusEventLog bonusEventLog1 = bonusEventLog.get(0);
            Date date = bonusEventLog1.getCreateTime();
            try {
                if (DateUtil.checkAllotSigin(date) == 0){
                    return new ResponseDTO(true,"200","该用户还没有签到","可以签到",1l);
                }
                else if (DateUtil.checkAllotSigin(date) == 1){
                    return new ResponseDTO(false,"201","已经签到了","不可以签到",1l);
                }
                else if (DateUtil.checkAllotSigin(date) == 2){
                    return new ResponseDTO(false,"202","数据出错了","不可以签到",1l);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return new ResponseDTO(true,"200","该用户还没有签到","可以签到",1l);
        }
    }
}
