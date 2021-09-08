/**
* Copyright (c) 2012 Partners In Health.  All rights reserved.
* The use and distribution terms for this software are covered by the
* Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
* which can be found in the file epl-v10.html at the root of this distribution.
* By using this software in any fashion, you are agreeing to be bound by
* the terms of this license.
* You must not remove this notice, or any other, from this software.
**/
package org.pih.warehouse.integration

import org.apache.commons.io.IOUtils
import org.pih.warehouse.integration.ftp.SecureFtpClient

import java.nio.charset.StandardCharsets

class FileTransferService {

    boolean transactional = true
    def grailsApplication

    SecureFtpClient getSecureFtpClient() {
        Map sftpConfig = grailsApplication.config.openboxes.integration.ftp.flatten()
        return new SecureFtpClient(sftpConfig)
    }

    def listMessages(boolean includeContent = false) {
        try {
            String directory = grailsApplication.config.openboxes.integration.ftp.directory
            def filenames = secureFtpClient.listFiles(directory)
            def messages = filenames.collect { String filename ->
                String source = "${directory}/${filename}"
                if (includeContent) {
                    def inputStream = secureFtpClient.retrieveFileAsInputStream(source)
                    String content = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name())
                    return [filename: filename, content: content]
                }
                return [filename:filename]
            }
            return messages
        } catch (Exception e) { }
        finally {
            secureFtpClient.disconnect()
        }
    }

    def retrieveMessage(String filename) {
        try {
            String directory = grailsApplication.config.openboxes.integration.ftp.directory
            String source = "${directory}/${filename}"
            def inputStream = secureFtpClient.retrieveFileAsInputStream(source)
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8.name())
        } finally {
            secureFtpClient.disconnect()
        }
    }

    def storeMessage(File file) {
        try {
            String directory = grailsApplication.config.openboxes.integration.ftp.directory
            String destination = "${directory}/${file.getName()}"
            secureFtpClient.storeFile(file, destination)
        } finally {
            secureFtpClient.disconnect()
        }
    }

    def storeMessage(String filename, String contents) {
        try {
            String directory = grailsApplication.config.openboxes.integration.ftp.directory
            String destination = "${directory}/${filename}"
            secureFtpClient.storeFile(filename, contents, destination)
        } finally {
            secureFtpClient.disconnect()
        }
    }

}
