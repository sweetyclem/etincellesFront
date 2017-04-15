package com.etincelles.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import com.etincelles.service.impl.UserSecurityService;
import com.etincelles.utility.SecurityUtility;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity( prePostEnabled = true )
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @SuppressWarnings( "unused" )
    @Autowired
    private Environment         env;

    @Autowired
    private UserSecurityService userSecurityService;

    private BCryptPasswordEncoder passwordEncoder() {
        return SecurityUtility.passwordEncoder();
    }

    private static final String[] PUBLIC_MATCHERS = {
            "/css/**",
            "/js/**",
            "/images/**",
            "/fonts/**",
            "/",
            "/myAccount",
            "/forgetPassword",
            "/login",
            "/updateUser",
            "/directoryIndex",
            "/directory",
            "/directorySearch",
            "/news",
            "/badRequestPage",
            "/post",
            "/userDetail"
    };

    @Override
    protected void configure( HttpSecurity http ) throws Exception {
        http
                .authorizeRequests().antMatchers( PUBLIC_MATCHERS ).permitAll()
                .and()
                .authorizeRequests()
                .antMatchers( "/calendar" ).authenticated();

        http
                .csrf().disable().cors().disable()
                .formLogin().failureUrl( "/login?error" ).defaultSuccessUrl( "/updateUserInfo" )
                .loginPage( "/login" ).permitAll()
                .and()
                .logout().logoutRequestMatcher( new AntPathRequestMatcher( "/logout" ) )
                .logoutSuccessUrl( "/?logout" ).deleteCookies( "remember-me" ).permitAll()
                .and()
                .rememberMe();
    }

    @Autowired
    public void configureGlobal( AuthenticationManagerBuilder auth ) throws Exception {
        auth.userDetailsService( userSecurityService ).passwordEncoder( passwordEncoder() );
    }
}
