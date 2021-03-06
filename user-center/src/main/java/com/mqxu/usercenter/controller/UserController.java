package com.mqxu.usercenter.controller;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.mqxu.usercenter.auth.CheckLogin;
import com.mqxu.usercenter.domain.dto.*;
import com.mqxu.usercenter.domain.entity.BonusEventLog;
import com.mqxu.usercenter.domain.entity.User;
import com.mqxu.usercenter.service.UserService;
import com.mqxu.usercenter.util.JwtOperator;
import io.jsonwebtoken.Claims;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mqxu
 * @description: 用户控制层
 */
@RestController
@RequestMapping(value = "/users")
@Api(tags = "用户接口", value = "提供用户相关的Rest API")
@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class UserController {
    private final UserService userService;
    private final WxMaService wxMaService;
    private final JwtOperator jwtOperator;

    @GetMapping(value = "/{id}")
    @CheckLogin
    @ApiOperation(value = "根据id查找用户信息", notes = "根据id查找用户信息")
    public User findUserById(@PathVariable Integer id) {
        log.info("根据id查找用户接口被请求了...");
        return this.userService.findById(id);
    }

    /**
     * 模拟生成token(假的登录)
     */
    @GetMapping("/gen-token")
    @ApiOperation(value = "模拟生成token", notes = "模拟生成token")
    public String genToken() {
        Map<String, Object> userInfo = new HashMap<>(3);
        userInfo.put("id", 1);
        userInfo.put("wxNickname", "许莫淇");
        userInfo.put("role", "admin");
        return this.jwtOperator.generateToken(userInfo);
    }

    @PostMapping(value = "/login")
    @ApiOperation(value = "用户登录", notes = "用户登录")
    public LoginRespDTO login(@RequestBody LoginDTO loginDTO) throws WxErrorException {
        log.info("请求登录接口：" + loginDTO + ">>>>>>>>>>>>>>>>>>>");
        String openId;
        //微信小程序登录，需要根据code请求openId
        if (loginDTO.getOpenId() == null) {
            // 微信服务端校验是否已经登录的结果
            WxMaJscode2SessionResult result = this.wxMaService.getUserService()
                    .getSessionInfo(loginDTO.getLoginCode());
            log.info(result.toString());
            //微信的openId，用户在微信这边的唯一标识
            openId = result.getOpenid();
        } else {
            openId = loginDTO.getOpenId();
        }
        // 查询用户是否注册，如果没有注册就插入一条数据，如果已经注册就返回其信息
        User user = userService.login(loginDTO, openId);
        // 颁发token，将id，微信昵称，角色写入载荷
        Map<String, Object> userInfo = new HashMap<>(3);
        userInfo.put("id", user.getId());
        userInfo.put("wxNickname", user.getWxNickname());
        userInfo.put("role", user.getRoles());
        String token = jwtOperator.generateToken(userInfo);

        log.info(
                "{}登录成功，生成的token = {}, 有效期到:{}",
                user.getWxNickname(),
                token,
                jwtOperator.getExpirationTime()
        );
        ResponseDTO responseDTO = this.userService.checkIsSign(UserSignInDTO.builder().userId(user.getId()).build());
        int isUserSignin = 0;
        if (responseDTO.getCode()=="200"){
            isUserSignin = 0;
        }else {
            isUserSignin = 1;
        }
        //构造返回结果
        return LoginRespDTO.builder()
                .user(UserRespDTO.builder()
                        .id(user.getId())
                        .wxNickname(user.getWxNickname())
                        .avatarUrl(user.getAvatarUrl())
                        .bonus(user.getBonus())
                        .roles(user.getRoles())
                        .build())
                .token(JwtTokenRespDTO
                        .builder()
                        .token(token)
                        .expirationTime(jwtOperator.getExpirationTime().getTime())
                        .build())
                .isUserSignIn(isUserSignin)
                .build();
    }

    @PutMapping(value = "/update-bonus")
    @ApiOperation(value = "修改用户积分", notes = "修改用户积分")
    public User updateBonus(@RequestBody UserAddBonusDTO userAddBonusDTO) {
        log.info("修改积分接口被请求了...");
        Integer userId = userAddBonusDTO.getUserId();
        userService.updateBonus(
                UserAddBonusMsgDTO.builder()
                        .userId(userId)
                        .bonus(userAddBonusDTO.getBonus())
                        .description("兑换分享")
                        .event("BUY")
                        .build()
        );
        return this.userService.findById(userId);
    }

    @GetMapping(value = "/my-bonus-log")
    @CheckLogin
    @ApiOperation(value = "查询积分明细", notes = "查询积分明细")
    public List<BonusEventLog> getBonusEvents(@RequestHeader(value = "X-Token", required = false) String token) {
        log.info("查询积分明细接口被请求了...");
        int userId = getUserIdFromToken(token);
        return userService.getBonusEventLogs(userId);
    }

    /**
     * @param token :token
     * @return userId: 用户id
     * @description: 封装一个从token中提取userId的方法
     */
    private int getUserIdFromToken(String token) {
        log.info(">>>>>>>>>>>token" + token);
        int userId = 0;
        String noToken = "no-token";
        if (!noToken.equals(token)) {
            Claims claims = this.jwtOperator.getClaimsFromToken(token);
            log.info(claims.toString());
            userId = (Integer) claims.get("id");
        } else {
            log.info("没有token");
        }
        return userId;
    }
    @PostMapping(value = "/sign")
    public ResponseDTO signIn(@RequestBody UserSignInDTO userSignInDTO){
        return userService.signIn(userSignInDTO);
    }
}
