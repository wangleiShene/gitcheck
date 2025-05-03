package com.mygittool.gitcheck;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.vcs.log.Hash;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Gitcheck extends AnAction {
    private static final Logger LOG = Logger.getInstance(Gitcheck.class);
    private ScheduledFuture<?> scheduledTask;

    @Override
    public void actionPerformed(AnActionEvent e) {

        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(true);
            return;
        }

        Project project = e.getProject();
        if (project == null || project.isDisposed()) return;

        Runnable task = () -> {
            GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
            if (repositoryManager == null) return;

            List<GitRepository> repositories = repositoryManager.getRepositories();
            if (repositories.isEmpty()) return;

            for (GitRepository repo : repositories) {
                if (!repo.getProject().getName().equals(project.getName())){
                    continue;
                }
                String currentHash = repo.getCurrentRevision();
                // 异步执行 Git 操作
                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                    GitLineHandler handler = new GitLineHandler(project, repo.getRoot(), GitCommand.FETCH);
                    Git.getInstance().runCommand(handler);
                });

                Hash masterHash = repo.getBranches().getHash(repo.getBranches().findBranchByName("origin/master"));
                LOG.info("current: "+currentHash+", master:" +masterHash);
                if (!(masterHash.asString().equals(currentHash))){
                    ApplicationManager.getApplication().invokeLater(() -> {
                        Notification notification = new Notification(
                                "gitcheck",
                                "GitCheck",
                                "New commits detected, please pull",
                                NotificationType.WARNING
                        );
                        Notifications.Bus.notify(notification, project);
                    });
                }
            }
        };

        scheduledTask = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(task, 60, 60 * 5, TimeUnit.SECONDS);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
