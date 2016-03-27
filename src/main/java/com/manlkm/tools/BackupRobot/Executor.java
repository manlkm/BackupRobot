/**
 * 
 */
package com.manlkm.tools.BackupRobot;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.web.util.UriUtils;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.sun.swing.internal.plaf.metal.resources.metal_zh_TW;

/**
 * @author manlkm
 *
 */
public class Executor {
	private static String HOST = null;
	
	private static String downloadToPath = null;
	
	private static String DOWNLOAD_FILE_NAME = "download.dat";
	
	private static boolean ENABLE_ROOT_LEVEL = false;
	
	private static int noOfFileToBeDownloaded = 0;
	
	private static Writer out = null;
	
	private static String webDavLoginUser = null;
	
	private static String webDavLoginPwd = null;
	
	private static String webDavSrcDirs[] = null;
	
	private static String senderEmail = null;
	
	private static String receiverEmail = null;
	
	private static String smtpHost = null;
	
	private static String runningIntervalMs = null;
	
	private static String runningDelayMs = null;
	
	/**
	 * @param args
	 * @throws IOException 
	 * @throws InterruptedException 
	 * @throws NumberFormatException 
	 */
	public static void main(String[] args) throws IOException, NumberFormatException, InterruptedException {
		/** Setting initialization **/
		if(args.length < 1){
			System.out.println("Usage: Executor <config path>");
			System.exit(1);
		}
		
		String configPath = args[0];
		String hostname = InetAddress.getLocalHost().getHostName();
		initSetting(configPath);
		
		while (true) {
			System.out.println("backup-robot started at " + new Date());
			noOfFileToBeDownloaded = 0;
			Thread.sleep(Integer.parseInt(runningDelayMs));
			Sardine sardine = SardineFactory.begin(webDavLoginUser, webDavLoginPwd);
			//String rootLevels[] = {"/photos/Man"};
			
			out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(FilenameUtils.concat(downloadToPath, DOWNLOAD_FILE_NAME)), "UTF-8"));
	        
			/** Generate the download list in advance **/
			try{
				for(String webDavSrcDir : webDavSrcDirs){
					traverseRes(webDavSrcDir, webDavSrcDir, sardine);
				}
			}catch(Exception e){
				StringWriter errors = new StringWriter();
				e.printStackTrace(new PrintWriter(errors));
				System.out.println(errors.toString());
				EmailSender.sendEmail(senderEmail, receiverEmail,smtpHost, 
						"[BackupRobot-"+hostname+"] Backup failed at " + new Date(),
						errors.toString());
				System.exit(1);
			}finally {
				out.close();
			}
			
			EmailSender.sendEmail(senderEmail, receiverEmail,smtpHost, 
					"[BackupRobot-"+hostname+"] Backup started at " + new Date(),
					"Starting to download " + noOfFileToBeDownloaded + " file(s)");
			System.out.println("Starting to download " + noOfFileToBeDownloaded + " file(s)");
			
			/** Download actually **/
			downloadFiles(sardine);
			EmailSender.sendEmail(senderEmail, receiverEmail,smtpHost, 
					"[BackupRobot-"+hostname+"] Backup completed at " + new Date(),
					"Finished to download " + noOfFileToBeDownloaded + " file(s)");
			
			System.out.println("backup-robot ended at " + new Date());
			System.out.println("Waiting " + runningIntervalMs + " ms for next round ...");
			Thread.sleep(Integer.parseInt(runningIntervalMs));
		}
		
	}
	
	private static void initSetting(String configPath) throws IOException{
		Properties props = new Properties();
		/*try(InputStream resourceStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("config.properties")) {
		    props.load(resourceStream);
		}*/
		props.load(new FileInputStream(new File(configPath)));
		
		HOST = props.getProperty("webdav.url");
		downloadToPath = props.getProperty("local.downloadTo");
		ENABLE_ROOT_LEVEL = Boolean.parseBoolean(props.getProperty("enableRootLevel"));
		webDavLoginUser = props.getProperty("webdav.username");
		webDavLoginPwd = props.getProperty("webdav.password");
		webDavSrcDirs = props.getProperty("webdav.srcdirs").split(",");
		senderEmail = props.getProperty("sender.email");
		receiverEmail = props.getProperty("receiver.email");
		smtpHost = props.getProperty("smtp.host");
		runningIntervalMs = props.getProperty("running.interval.ms");
		runningDelayMs = props.getProperty("running.delay.ms");
	}
	
	private static void traverseRes(String path, String rootLevel, Sardine sardine) throws IOException{
		//List<DavResource> resources = sardine.list(UriUtils.encodeHttpUrl(HOST + path, "UTF-8"), 1);
		List<DavResource> resources = sardine.list(HOST + path);
		int rootLevelCount = 0;
		for (DavResource res : resources)
		{
			if(rootLevelCount > 0){
				//System.out.println(res);
				String localPath = ENABLE_ROOT_LEVEL?res.toString():Paths.get(downloadToPath, res.toString().replace(rootLevel, "")).toString();
				//add to download file if the file does not exist in local or size not match
				if(!isLocalExist(localPath) || (isLocalExist(localPath) && res.getContentLength().longValue() != new File(localPath).length())){
					Path toBeCreated = Paths.get(downloadToPath, res.toString().replace(rootLevel, ""));
					if(!res.isDirectory()){
						out.write("F|"+UriUtils.encodeHttpUrl(HOST+res.toString(), "UTF-8") + "|" + toBeCreated + System.lineSeparator());
					}
					else{
						out.write("D|"+UriUtils.encodeHttpUrl(HOST+res.toString(), "UTF-8") + "|" + toBeCreated + System.lineSeparator());
					}
					
					noOfFileToBeDownloaded++;
					toBeCreated = null;
				}
				if(res.isDirectory()){
					traverseRes(res.getHref().toString(), rootLevel, sardine);
				}
			}
			rootLevelCount++;
			
		}
		return;
	}

	private static void downloadFiles(Sardine sardine) throws IOException{
		BufferedReader in = null;
		try {
			in = new BufferedReader(new InputStreamReader(
					new FileInputStream(FilenameUtils.concat(downloadToPath, DOWNLOAD_FILE_NAME)), "UTF8"));
			String str;

			int line = 0;
			while ((str = in.readLine()) != null) {
				line++;
				
				//pos 0:file type
				//pos 1:full path of the file/dir in server
				//pos 2:being download path in local
				String info[] = str.split("\\|");
				if(info.length != 3){
					throw new Exception("Incorrect format in file line: " + line);
				}
				//System.out.println(">> Downloading " + info[1] + " > " + info[2]);
				File file = new File(info[2]);
				
				if(!"D".equals(info[0])){ //not a directory
					InputStream inStream = sardine.get(info[1]);
					OutputStream outputStream = new FileOutputStream(file);
					IOUtils.copy(inStream, outputStream);
					outputStream.close();
				}
				else{
					file.mkdirs();
				}
				
				//System.out.println("<< Download completed");
			}

		} catch (UnsupportedEncodingException e){
			System.out.println(e.getMessage());
		} catch (IOException e){
			System.out.println(e.getMessage());
		} catch (Exception e){
			System.out.println(e.getMessage());
		} finally {
			in.close();
		}
	}
	
	private static boolean isLocalExist(String path){
		return new File(path).exists();
	}
}
