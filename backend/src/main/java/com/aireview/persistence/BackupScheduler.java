package com.aireview.persistence;

import com.aireview.common.BusinessException;
import com.aireview.dto.PersistenceSetting;
import com.aireview.service.BackupService;
import com.aireview.service.InstanceSettingService;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * 每分钟检查一次后台配置里的 cron 是否命中。这样改周期不需要重启进程，
 * 也不必把 cron 写进 yml 后靠 @Scheduled(cron) 静态绑定。
 */
@Component
public class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    private final InstanceSettingService settingService;
    private final BackupService backupService;
    private final boolean schedulerEnabled;

    /** 上次检查时间；初始化为构造时刻，避免刚启动就补跑一个过去的周期。 */
    private LocalDateTime lastCheck = LocalDateTime.now();

    public BackupScheduler(InstanceSettingService settingService, BackupService backupService,
        @Value("${ai-review.persistence.scheduler-enabled:true}") boolean schedulerEnabled) {
        this.settingService = settingService;
        this.backupService = backupService;
        this.schedulerEnabled = schedulerEnabled;
    }

    @Scheduled(fixedDelayString = "${ai-review.persistence.check-interval-ms:60000}", initialDelay = 60000)
    public void check() {
        if (!schedulerEnabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            PersistenceSetting setting = settingService.getPersistence();
            if (setting.enabled() && setting.autoBackup()) {
                CronExpression cron = CronExpression.parse(setting.backupCron());
                LocalDateTime due = cron.next(lastCheck);
                if (due != null && !due.isAfter(now)) {
                    backupService.start("AUTO");
                    log.info("自动快照已触发：{}", setting.backupCron());
                }
            }
            // 保留期清理与自动备份解耦：即使 autoBackup 关闭也按天清理
            backupService.cleanExpired();
        } catch (BusinessException exception) {
            log.debug("本次调度跳过：{}", exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("自动快照调度异常：{}", exception.getMessage());
        } finally {
            lastCheck = now;
        }
    }
}
