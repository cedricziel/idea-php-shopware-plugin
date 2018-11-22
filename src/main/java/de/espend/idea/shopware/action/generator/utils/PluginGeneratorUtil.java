package de.espend.idea.shopware.action.generator.utils;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ScriptRunnerUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import com.jetbrains.php.util.PhpConfigurationUtil;
import de.espend.idea.shopware.ShopwareApplicationSettings;
import de.espend.idea.shopware.action.generator.dict.PluginGeneratorSettings;
import fr.adrienbrault.idea.symfony2plugin.installer.SymfonyInstallerUtil;
import fr.adrienbrault.idea.symfony2plugin.util.IdeHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class PluginGeneratorUtil {

    private static final long CHECKING_TIMEOUT_IN_MILLISECONDS = 1000L;

    /**
     * Download or locally copy cli tools phar
     */
    @Nullable
    private static VirtualFile getCliToolsPharFile(@NotNull Project project) {
        VirtualFile cliFile = VfsUtil.findFileByIoFile(new File(project.getBasePath() + "/sw-cli-tools.phar"), true);
        if(cliFile != null) {
            return cliFile;
        }

        String cliToolsPharUrl = ShopwareApplicationSettings.getInstance().cliToolsPharUrl;
        // http download
        if(cliToolsPharUrl.startsWith("http")) {
            PhpConfigurationUtil.downloadFile(project, null, project.getBasePath(), ShopwareApplicationSettings.getInstance().cliToolsPharUrl, "sw-cli-tools.phar");
            return VfsUtil.findFileByIoFile(new File(project.getBasePath() + "/sw-cli-tools.phar"), true);
        }

        try {
            FileUtil.copy(
                new File(cliToolsPharUrl),
                new File(project.getBasePath() + "/sw-cli-tools.phar")
            );
        } catch (IOException ignored) {
        }

        return null;
    }

    public static void installPlugin(@NotNull Project project, @NotNull PluginGeneratorSettings settings) {

        // download cli tools, if not existing locally
        VirtualFile cliFile = getCliToolsPharFile(project);
        if (cliFile == null) {
            showErrorNotification(project, "No CLI-Tools phar found");
            return;
        }

        List<String> commands = generateCommand(settings);

        String[] myCommand = ArrayUtil.toStringArray(commands);

        final StringBuilder outputBuilder = new StringBuilder();
        try {
            OSProcessHandler processHandler = ScriptRunnerUtil.execute(myCommand[0], project.getBaseDir().getPath(), null, Arrays.copyOfRange(myCommand, 1, myCommand.length));

            processHandler.addProcessListener(new ProcessAdapter() {
                @Override
                public void onTextAvailable(@NotNull ProcessEvent event, @NotNull com.intellij.openapi.util.Key outputType) {
                    String text = event.getText();
                    outputBuilder.append(text);
                }
            });

            processHandler.startNotify();
            for (;;){
                boolean finished = processHandler.waitFor(CHECKING_TIMEOUT_IN_MILLISECONDS);
                if (finished) {
                    break;
                }
            }
        }
        catch (ExecutionException e) {
            showErrorNotification(project, e.getMessage());
            return;
        }

        String output = outputBuilder.toString();
        if (output.toLowerCase().contains("exception")) {

            String message = SymfonyInstallerUtil.formatExceptionMessage(output);
            if(message == null) {
                message = "The unexpected happens...";
            }

            showErrorNotification(project, message);
            return;
        }

        // delete cli tools
        FileUtil.delete(VfsUtil.virtualToIoFile(cliFile));

        // move into correct plugin folder
        String newDir = project.getBasePath() + "/engine/Shopware/Plugins/Local/" + settings.getNamespace() + "/" + settings.getPluginName();
        if (FileUtil.canWrite(newDir)) {
            return;
        }

        FileUtil.createDirectory(new File(newDir));
        FileUtil.moveDirWithContent(new File(project.getBasePath() + "/" + settings.getPluginName()), new File(newDir));

        // open bootstrap file
        VirtualFile fileByIoFile = VfsUtil.findFileByIoFile(new File(newDir + "/Bootstrap.php"), true);
        if(fileByIoFile == null) {
            return;
        }
        final PsiFile file = PsiManager.getInstance(project).findFile(fileByIoFile);
        if (file == null) {
            return;
        }
        IdeHelper.navigateToPsiElement(file);
    }

    @NotNull
    private static List<String> generateCommand(@NotNull PluginGeneratorSettings settings) {
        List<String> commands = new ArrayList<>();

        commands.add(settings.getInterpreter());
        commands.add("sw-cli-tools.phar");
        commands.add("plugin:create");

        if (settings.getAddDummyFilter()) {
            commands.add("--haveFilter");
        }

        if (settings.getAddDummyFrontendController()) {
            commands.add("--haveFrontend");
        }

        if (settings.getAddDummyBackendController()) {
            commands.add("--haveBackend");
        }

        if (settings.getAddDummyModels()) {
            commands.add("--haveModels");
        }

        if (settings.getAddDummyCommand()) {
            commands.add("--haveCommands");
        }

        if (settings.getAddDummyWidget()) {
            commands.add("--haveWidget");
        }

        if (settings.getAddDummyApi()) {
            commands.add("--haveApi");
        }

        if (settings.isLegacyStructure()) {
            commands.add("--legacy");
            commands.add("--namespace=" + settings.getNamespace());
        }

        commands.add(settings.getPluginName());
        return commands;
    }

    private static void showErrorNotification(@NotNull Project project, @NotNull String content)
    {
        Notifications.Bus.notify(new Notification(SymfonyInstallerUtil.INSTALLER_GROUP_DISPLAY_ID, "Shopware-Plugin", content, NotificationType.ERROR, null), project);
    }
}
