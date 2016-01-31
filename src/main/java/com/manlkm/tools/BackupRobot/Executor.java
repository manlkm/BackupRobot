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
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.web.util.UriUtils;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;

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
	
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		/** Setting initialization **/
		initSetting();
		
		Sardine sardine = SardineFactory.begin(webDavLoginUser, webDavLoginPwd);
		String rootLevels[] = {"/photos/Man"};
		
		out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(FilenameUtils.concat(downloadToPath, DOWNLOAD_FILE_NAME)), "UTF-8"));
        
		/** Genereate the download list in advance **/
		for(String rootLevel : rootLevels){
			try{
				traverseRes(rootLevel, rootLevel, sardine);
			}catch(Exception e){
				e.printStackTrace();
			}finally {
				out.close();
			}
		}
		
		System.out.println("No. of files downloaded: " + noOfFileToBeDownloaded);
		
		/** Download actually **/
		downloadFiles(sardine);
		
		
		
	}
	
	private static void initSetting() throws IOException{
		Properties props = new Properties();
		try(InputStream resourceStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("config.properties")) {
		    props.load(resourceStream);
		}
		
		HOST = props.getProperty("webdav.url");
		downloadToPath = props.getProperty("local.downloadTo");
		ENABLE_ROOT_LEVEL = Boolean.parseBoolean(props.getProperty("enableRootLevel"));
		webDavLoginUser = props.getProperty("webdav.username");
		webDavLoginPwd = props.getProperty("webdav.password");
	}
	
	private static void traverseRes(String path, String rootLevel, Sardine sardine) throws IOException{
		List<DavResource> resources = sardine.list(HOST + path, 1);
		int rootLevelCount = 0;
		for (DavResource res : resources)
		{
			if(rootLevelCount > 0){
				System.out.println(res);
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
				System.out.println(">> Downloading " + info[1] + " > " + info[2]);
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
				
				System.out.println("<< Download completed");
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
