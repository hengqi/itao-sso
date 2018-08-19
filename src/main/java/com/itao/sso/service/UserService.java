package com.itao.sso.service;

import com.itao.common.result.ITaoResult;
import com.itao.manager.dal.ItaoUserDO;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public interface UserService {

	ITaoResult checkData(String content, Integer type);

	ITaoResult registUser(ItaoUserDO user);

	ITaoResult userLogin(String username, String password, HttpServletRequest request,
                           HttpServletResponse response);

	ITaoResult getUserByToken(String token);

	ITaoResult logout(String token);

}
