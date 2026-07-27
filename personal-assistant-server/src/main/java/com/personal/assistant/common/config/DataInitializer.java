package com.personal.assistant.common.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.personal.assistant.module.auth.entity.UserAccount;
import com.personal.assistant.module.auth.mapper.UserAccountMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 系统启动时初始化默认管理员账号（仅首次，已存在则跳过）。
 * 默认账号 admin / admin123，首次登录后请修改密码。
 */
@Slf4j
@Component
public class DataInitializer implements ApplicationRunner {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserAccountMapper userAccountMapper, PasswordEncoder passwordEncoder) {
        this.userAccountMapper = userAccountMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long count = userAccountMapper.selectCount(
                new LambdaQueryWrapper<UserAccount>().eq(UserAccount::getUsername, "admin"));
        if (count > 0) {
            return;
        }
        UserAccount admin = new UserAccount();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setDisplayName("管理员");
        admin.setRole("ADMIN");
        admin.setEnabled(true);
        LocalDateTime now = LocalDateTime.now();
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        userAccountMapper.insert(admin);
        log.info("已初始化默认管理员账号 admin，请登录后尽快修改密码");
    }
}
