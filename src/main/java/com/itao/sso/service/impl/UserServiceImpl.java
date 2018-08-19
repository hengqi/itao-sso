package com.itao.sso.service.impl;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.itao.common.result.ITaoResult;
import com.itao.common.utils.CookieUtils;
import com.itao.common.utils.JsonUtils;
import com.itao.manager.dal.ItaoUserDO;
import com.itao.manager.dal.ItaoUserDOExample;
import com.itao.manager.mapper.ItaoUserDOMapper;
import com.itao.sso.redis.RedisCacheManager;
import com.itao.sso.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;


@Service
public class UserServiceImpl implements UserService {

	@Autowired
	private ItaoUserDOMapper userMapper;

    @Autowired
    private RedisCacheManager redisCacheManager;


    @Value("${REDIS_USER_SESSION_KEY}")
	private String REDIS_USER_SESSION_KEY;
	
	@Value("${SSO_SESSION_EXPIRE}")
	private Integer SSO_SESSION_EXPIRE;
	
	@Override
	public ITaoResult checkData(String content, Integer type) {
		// 创建查询条件
		ItaoUserDOExample example = new ItaoUserDOExample();
		ItaoUserDOExample.Criteria criteria = example.createCriteria();
		// 对数据进行校验：1、2、3分别代表username、phone、email
		// 用户名校验
		if (1 == type) {
			criteria.andUsernameEqualTo(content);
			// 电话校验
		} else if (2 == type) {
			criteria.andPhoneEqualTo(content);
			// email校验
		} else {
			criteria.andEmailEqualTo(content);
		}
		// 执行查询
		List<ItaoUserDO> list = userMapper.selectByExample(example);
		if (list == null || list.size() == 0) {
			return ITaoResult.ok(true);
		}
		return ITaoResult.ok(false);
	}
	
	
	public ITaoResult registUser(ItaoUserDO user) {
		user.setUpdated(new Date());
		user.setCreated(new Date());
		//md5加密
		user.setPassword(DigestUtils.md5DigestAsHex(user.getPassword().getBytes()));
		userMapper.insert(user);
		return ITaoResult.ok();
	}


	public ITaoResult userLogin(String username, String password,
			HttpServletRequest request, HttpServletResponse response) {

		ItaoUserDOExample example = new ItaoUserDOExample();
		ItaoUserDOExample.Criteria criteria = example.createCriteria();
		criteria.andUsernameEqualTo(username);
		List<ItaoUserDO> list = userMapper.selectByExample(example);
		//如果没有此用户名
		if (null == list || list.size() == 0) {
			return ITaoResult.build(400, "用户名或密码错误");
		}
		ItaoUserDO user = list.get(0);
		//比对密码
		if (!DigestUtils.md5DigestAsHex(password.getBytes()).equals(user.getPassword())) {
			return ITaoResult.build(400, "用户名或密码错误");
		}
		//生成token
		String token = UUID.randomUUID().toString();
		//保存用户之前，把用户对象中的密码清空。
		user.setPassword(null);
		//把用户信息写入redis
        redisCacheManager.set(REDIS_USER_SESSION_KEY + ":" + token, JsonUtils.objectToJson(user));
		//设置session的过期时间
        redisCacheManager.expire(REDIS_USER_SESSION_KEY + ":" + token, SSO_SESSION_EXPIRE);
		//添加写Cookie的逻辑, cookie的有效期是关闭浏览器就失效
		CookieUtils.setCookie(request, response, "TT_TOKEN", token);
		//返回token
		return ITaoResult.ok(token);
	}

	@Override
	public ITaoResult getUserByToken(String token) {
		
		//根据token从redis中查询用户信息
		String json = (String) redisCacheManager.get(REDIS_USER_SESSION_KEY + ":" + token);
		//判断是否为空
		if (StringUtils.isBlank(json)) {
			return ITaoResult.build(400, "此session已经过期，请重新登录");
		}
		//更新过期时间
        redisCacheManager.expire(REDIS_USER_SESSION_KEY + ":" + token, SSO_SESSION_EXPIRE);
		//返回用户信息
		return ITaoResult.ok(JsonUtils.jsonToPojo(json, ItaoUserDO.class));
	}
	
	@Override
	public ITaoResult logout(String token) {
//		从redis中删除用户key
        redisCacheManager.del(REDIS_USER_SESSION_KEY + ":" + token);
		//返回用户信息
		return ITaoResult.ok();
	}

}
