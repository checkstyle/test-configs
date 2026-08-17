///////////////////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code and other text files for adherence to a set of rules.
// Copyright (C) 2001-2026 the original author or authors.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
///////////////////////////////////////////////////////////////////////////////////////////////

package com.github.checkstyle.difftool;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class consisting of utilities needed to execute commands.
 */
public final class CommandsUtil {
    /**
     * Logger instance for logging messages specific to GitCommands operations.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandsUtil.class);

    /**
     * Number of times a command will be retried if it fails.
     */
    private static final int DEFAULT_RETRY_COUNT = 5;

    /**
     * Time (in seconds) to wait between retry attempts.
     */
    private static final int SLEEP_DURATION_SECONDS = 15;

    /** Private constructor to prevent instantiation of this utility class. */
    private CommandsUtil() {
        // Utility class, hide constructor
    }

    /**
     * Executes a command in the specified directory.
     *
     * @param cmd The command to execute.
     * @param dir The directory in which to execute the command.
     * @throws IOException If an I/O error occurs during command execution.
     * @throws InterruptedException If the process is interrupted while waiting.
     * @throws CommandExecutionException If the command exits with a non-zero status.
     */
    public static void executeCmd(final String cmd, final File dir)
            throws IOException, InterruptedException {
        LOGGER.info("Running command: " + cmd);
        final ProcessBuilder processBuilder =
                new ProcessBuilder(getOsSpecificCmd(cmd).split("\\s+"));
        processBuilder.directory(dir);
        processBuilder.inheritIO();
        final Process process = processBuilder.start();
        final int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new CommandExecutionException("Command execution failed", exitCode);
        }
    }

    /**
     * Executes a command in the current working directory.
     *
     * @param cmd the command to execute
     * @throws IOException if an I/O error occurs while executing the command
     * @throws InterruptedException if the process is interrupted while waiting
     */
    public static void executeCmd(final String cmd) throws IOException, InterruptedException {
        executeCmd(cmd, new File("").getAbsoluteFile());
    }

    /**
     * Returns the command string adjusted for the operating system.
     *
     * @param cmd the original command
     * @return the OS-specific command string
     */
    public static String getOsSpecificCmd(final String cmd) {
        if (System.getProperty("os.name").toLowerCase(Locale.getDefault()).contains("windows")) {
            return "cmd /c " + cmd;
        }
        return cmd;
    }

    /**
     * Constructs a path string using the appropriate file separator for the operating system.
     *
     * @param name the path components
     * @return the OS-specific path string
     */
    public static String getOsSpecificPath(final String... name) {
        return String.join(File.separator, name);
    }

    /**
     * Executes a command with retry logic.
     *
     * @param cmd   The command to execute.
     * @param dir   The directory to execute the command in.
     * @param retry The number of retry attempts.
     * @throws IllegalStateException If the command fails after all retries.
     */
    public static void executeCmdWithRetry(final String cmd, final File dir, final int retry) {
        final String osSpecificCmd = getOsSpecificCmd(cmd);
        int left = retry;
        while (left > 0) {
            try {
                final ProcessBuilder processBuilder =
                        new ProcessBuilder(osSpecificCmd.split("\\s+"));
                processBuilder.directory(dir);
                processBuilder.inheritIO();
                final Process process = processBuilder.start();
                final int exitCode = process.waitFor();
                if (exitCode == 0) {
                    return;
                }
                left--;
                if (left > 0) {
                    TimeUnit.SECONDS.sleep(SLEEP_DURATION_SECONDS);
                }
            }
            catch (IOException | InterruptedException ex) {
                LOGGER.error("Error executing command: " + ex.getMessage());
                left--;
            }
        }
        throw new IllegalStateException("Error executing command: " + cmd);
    }

    /**
     * Executes a command with retry mechanism.
     *
     * @param cmd The command to execute.
     * @throws IllegalStateException if the command fails after retries.
     */
    public static void executeCmdWithRetry(final String cmd) {
        executeCmdWithRetry(cmd, new File("").getAbsoluteFile(), DEFAULT_RETRY_COUNT);
    }

    /**
     * Custom runtime exception for handling command execution failures.
     * Includes the exit code in the error message.
     */
    public static class CommandExecutionException extends RuntimeException {
        public static final long serialVersionUID = 1L;

        /**
         * Constructs a CommandExecutionException with a message and exit code.
         *
         * @param message the detail message to be included in the exception
         * @param exitCode the exit code associated with the command execution failure
         */
        public CommandExecutionException(final String message, final int exitCode) {
            super(message + " (Exit code: " + exitCode + ")");
        }
    }
}
