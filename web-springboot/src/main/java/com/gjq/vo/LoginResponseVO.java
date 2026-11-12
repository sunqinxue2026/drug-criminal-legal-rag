package com.gjq.vo;

import lombok.Data;

@Data
public class LoginResponseVO {
    private String token;
    private String tokenType;
    private UserVO user;
}
