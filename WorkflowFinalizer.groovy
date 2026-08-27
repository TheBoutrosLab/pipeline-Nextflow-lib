import nextflow.script.WorkflowMetadata
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class WorkflowFinalizer {

    private static final DateTimeFormatter LOG_TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private static String formatLogLine(String level, String message) {
        String timestamp = LocalDateTime.now().format(LOG_TIMESTAMP_FORMAT);
        return "${timestamp} [${level}] WorkflowFinalizer - ${message}";
    }

    static void completeWorkflow(WorkflowMetadata workflow, Map params) {
        if (!workflow.success) {
            return;
        }

        // Tar process log directory from pipeline run
        tarProcessLogs(params);
    }

    private static void tarProcessLogs(Map params) {
        String log_dir_key = "log_output_dir";
        if (!params.containsKey(log_dir_key)) {
            return
        }

        Object log_output_dir = params[log_dir_key];
        if (log_output_dir == null || log_output_dir.toString().trim().isEmpty()) {
            System.err.println(formatLogLine("ERROR", "Cannot archive logs: '${log_dir_key}' must not be null or blank"));
            return;
        }

        String process_log_path = "${log_output_dir}/process-log";

        File dir = new File(process_log_path);

        if (!dir.isDirectory()) {
            return;
        }

        File archive = new File("${process_log_path}.tar.gz");

        System.out.println(formatLogLine("INFO", "Archiving logs: ${dir} -> ${archive}"));

        /*
         * Tar while only maintaining relative paths in tarball
         */
        List<String> command = [
            'tar',
            '-czf',
            archive.absolutePath,
            '-C',
            dir.parentFile.absolutePath,
            dir.name
        ]

        Process process;
        try {
            process = command.execute();
        } catch (IOException exception) {
            System.err.println(formatLogLine("ERROR", "Failed to start log archiver: ${exception.message}"));
            return;
        }

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        process.consumeProcessOutput(stdout, stderr);

        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            System.err.println(formatLogLine("ERROR", "Log archiving was interrupted"));
            return;
        }

        if (exitCode != 0) {
            System.err.println(formatLogLine("ERROR", "Failed to archive log directory: ${stderr}"));
            return;
        }

        if (!archive.isFile()) {
            System.err.println(formatLogLine("ERROR", "Archive was not created: ${archive}"));
            return;
        }

        System.out.println(formatLogLine("INFO", "Log archive created: ${archive}"));


        // Only remove the source directory after tar completes successfully
        if (!dir.deleteDir()) {
            System.err.println(formatLogLine("ERROR", "Failed to remove original log directory: ${dir}"));
            return;
        }

        System.out.println(formatLogLine("INFO", "Original log directory removed: ${dir}"));
    }
}
