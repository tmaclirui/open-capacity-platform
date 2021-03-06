
package com.open.capacity.uaa.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.code.RandomValueAuthorizationCodeServices;
import org.springframework.security.oauth2.provider.error.OAuth2AccessDeniedHandler;
import org.springframework.security.oauth2.provider.error.WebResponseExceptionTranslator;
import org.springframework.security.oauth2.provider.expression.OAuth2WebSecurityExpressionHandler;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;
import org.springframework.security.oauth2.provider.token.store.JwtTokenStore;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.open.capacity.common.auth.props.PermitUrlProperties;
import com.open.capacity.common.constant.UaaConstant;
import com.open.capacity.common.feign.FeignInterceptorConfig;
import com.open.capacity.common.rest.RestTemplateConfig;
import com.open.capacity.uaa.server.service.RedisAuthorizationCodeServices;
import com.open.capacity.uaa.server.service.RedisClientDetailsService;

/**
 * @author owen 624191343@qq.com
 * @version 创建时间：2017年11月12日 上午22:57:51
 * blog: https://blog.51cto.com/13005375 
 * code: https://gitee.com/owenwangwen/open-capacity-platform
 */

@Configuration
@SuppressWarnings("all")
@Import({RestTemplateConfig.class,FeignInterceptorConfig.class})
public class UAAServerConfig {

   

    /**
     * 声明 ClientDetails实现
     */
    @Bean
    public RedisClientDetailsService redisClientDetailsService(DataSource dataSource , RedisTemplate<String, Object> redisTemplate ) {
        RedisClientDetailsService clientDetailsService = new RedisClientDetailsService(dataSource);
        clientDetailsService.setRedisTemplate(redisTemplate);
        return clientDetailsService;
    }


    @Bean
    public RandomValueAuthorizationCodeServices authorizationCodeServices(RedisTemplate<String, Object> redisTemplate) {
        RedisAuthorizationCodeServices redisAuthorizationCodeServices = new RedisAuthorizationCodeServices();
        redisAuthorizationCodeServices.setRedisTemplate(redisTemplate);
        return redisAuthorizationCodeServices;
    }
    

    /**
     * @author owen 624191343@qq.com
     * @version 创建时间：2017年11月12日 上午22:57:51 默认token存储在内存中
     * DefaultTokenServices默认处理
     */
    @Component
    @Configuration
    @EnableAuthorizationServer
    @AutoConfigureAfter(AuthorizationServerEndpointsConfigurer.class)
    public class AuthorizationServerConfig extends AuthorizationServerConfigurerAdapter {
        /**
         * 注入authenticationManager 来支持 password grant type
         */
        @Autowired
        private AuthenticationManager authenticationManager;

        @Autowired
        private UserDetailsService userDetailsService;
        @Autowired(required = false)
        private TokenStore tokenStore;

   
        @Autowired(required = false)
        private JwtAccessTokenConverter jwtAccessTokenConverter;

       
		@Autowired
        private WebResponseExceptionTranslator webResponseExceptionTranslator;

        @Autowired
        private RedisClientDetailsService redisClientDetailsService;

        @Autowired(required = false)
        private RandomValueAuthorizationCodeServices authorizationCodeServices;


        /**
         * 配置身份认证器，配置认证方式，TokenStore，TokenGranter，OAuth2RequestFactory
         */
		public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
			
			
			//通用处理
			endpoints.tokenStore(tokenStore).authenticationManager(authenticationManager)
            // 支持
            .userDetailsService(userDetailsService);
			
			if(tokenStore instanceof JwtTokenStore){
				endpoints.accessTokenConverter(jwtAccessTokenConverter);
			}
             
            //处理授权码
            endpoints.authorizationCodeServices(authorizationCodeServices);
            // 处理 ExceptionTranslationFilter 抛出的异常
            endpoints.exceptionTranslator(webResponseExceptionTranslator);

        }

        /**
         * 配置应用名称 应用id
         * 配置OAuth2的客户端相关信息
         */
        @Override
        public void configure(ClientDetailsServiceConfigurer clients) throws Exception {

       
            clients.withClientDetails(redisClientDetailsService);
            redisClientDetailsService.loadAllClientToCache();
        }

        /**
         * 对应于配置AuthorizationServer安全认证的相关信息，创建ClientCredentialsTokenEndpointFilter核心过滤器
         */
        @Override
        public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {
        	
            // url:/oauth/token_key,exposes
            security.tokenKeyAccess("permitAll()")
                    /// public key for token
                    /// verification if using
                    /// JWT tokens
                    // url:/oauth/check_token
                    .checkTokenAccess("isAuthenticated()")
                    // allow check token
                    .allowFormAuthenticationForClients();
 
        }

    }

    @Configuration
    @EnableResourceServer
    @EnableConfigurationProperties(PermitUrlProperties.class)
    public class ResourceServerConfig extends ResourceServerConfigurerAdapter {

        @Autowired
        private PermitUrlProperties permitUrlProperties;
        @Autowired(required = false)
        private TokenStore tokenStore;
        @Autowired 
    	private ObjectMapper objectMapper ; //springmvc启动时自动装配json处理类
        
        @Autowired
    	private OAuth2WebSecurityExpressionHandler expressionHandler;

        public void configure(WebSecurity web) throws Exception {
            web.ignoring().antMatchers("/health");
            web.ignoring().antMatchers("/oauth/user/token");
            web.ignoring().antMatchers("/oauth/client/token");
        }
        
        @Override
    	public void configure(ResourceServerSecurityConfigurer resources) throws Exception {

    		if (tokenStore != null) {
    			resources.tokenStore(tokenStore);
    		}  
    		resources.stateless(true);
    		resources.expressionHandler(expressionHandler);
    		// 自定义异常处理端口 
    		resources.authenticationEntryPoint(new AuthenticationEntryPoint() {

    			@Override
    			public void commence(HttpServletRequest request, HttpServletResponse response,
    					AuthenticationException authException) throws IOException, ServletException {
    				
    				Map<String ,String > rsp =new HashMap<>();  
    				
    				response.setStatus(HttpStatus.UNAUTHORIZED.value() );
    				
    				rsp.put("resp_code", HttpStatus.UNAUTHORIZED.value() + "") ;
                    rsp.put("resp_msg", authException.getMessage()) ;
                    
                    response.setContentType("application/json;charset=UTF-8");
        			response.getWriter().write(objectMapper.writeValueAsString(rsp));
        			response.getWriter().flush();
        			response.getWriter().close();

    			}
    		});
    		resources.accessDeniedHandler(new OAuth2AccessDeniedHandler(){
        	    
        	    @Override
        	    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException authException) throws IOException, ServletException {

        	    	Map<String ,String > rsp =new HashMap<>();  
        	    	response.setContentType("application/json;charset=UTF-8");

        	        response.setStatus(HttpStatus.UNAUTHORIZED.value() );
    				
    				rsp.put("resp_code", HttpStatus.UNAUTHORIZED.value() + "") ;
                    rsp.put("resp_msg", authException.getMessage()) ;
                    
                    response.setContentType("application/json;charset=UTF-8");
        			response.getWriter().write(objectMapper.writeValueAsString(rsp));
        			response.getWriter().flush();
        			response.getWriter().close();
        	        
        	    }
        	});
    		
    	}

        @Override
        public void configure(HttpSecurity http) throws Exception {
            http.requestMatcher(
                    /**
                     * 判断来源请求是否包含oauth2授权信息
                     */
                    new RequestMatcher() {
                        private AntPathMatcher antPathMatcher = new AntPathMatcher();

                        @Override
                        public boolean matches(HttpServletRequest request) {
                            // 请求参数中包含access_token参数
                            if (request.getParameter(OAuth2AccessToken.ACCESS_TOKEN) != null) {
                                return true;
                            }

                            // 头部的Authorization值以Bearer开头
                            String auth = request.getHeader(UaaConstant.AUTHORIZTION);
                            if (auth != null) {
                                if (auth.startsWith(OAuth2AccessToken.BEARER_TYPE)) {
                                    return true;
                                }
                            }
                            
                            // 认证中心url特殊处理，返回true的，不会跳转login.html页面
                            if (antPathMatcher.match(request.getRequestURI(), "/api-auth/oauth/userinfo")) {
                                return true;
                            }
                            if (antPathMatcher.match(request.getRequestURI(), "/api-auth/oauth/remove/token")) {
                                return true;
                            }
                            if (antPathMatcher.match(request.getRequestURI(), "/api-auth/oauth/get/token")) {
                                return true;
                            }
                            if (antPathMatcher.match(request.getRequestURI(), "/api-auth/oauth/refresh/token")) {
                                return true;
                            }

                            if (antPathMatcher.match(request.getRequestURI(), "/api-auth/oauth/token/list")) {
                                return true;
                            }

                            if (antPathMatcher.match("/**/clients/**", request.getRequestURI())) {
                                return true;
                            }

                            if (antPathMatcher.match("/**/services/**", request.getRequestURI())) {
                                return true;
                            }
                            if (antPathMatcher.match("/**/redis/**", request.getRequestURI())) {
                                return true;
                            }
                            
                            return false;
                        }
                    }

            ).authorizeRequests().antMatchers(permitUrlProperties.getIgnored()).permitAll()
            .requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll()
            .anyRequest()
                    .authenticated();
        }

    }


}
