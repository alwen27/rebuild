/*
Copyright (c) REBUILD <https://getrebuild.com/> and its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.web.common;

import cn.devezhao.commons.CodecUtils;
import cn.devezhao.commons.web.ServletUtils;
import com.rebuild.server.Application;
import com.rebuild.server.helper.QiniuCloud;
import com.rebuild.server.helper.SysConfiguration;
import com.rebuild.web.BaseControll;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 文件下载/查看
 * 
 * @author devezhao
 * @since 01/03/2019
 */
@RequestMapping("/filex/")
@Controller
public class FileDownloader extends BaseControll {

	// 图片缓存时间
	private static final int IMG_CACHE_TIME = 60 * 2;

	@RequestMapping(value = "img/**", method = RequestMethod.GET)
	public void viewImg(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String filePath = request.getRequestURI();
		filePath = filePath.split("/filex/img/")[1];
		
		ServletUtils.addCacheHead(response, IMG_CACHE_TIME);

		if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
			response.sendRedirect(filePath);
			return;
		}

		final boolean temp = BooleanUtils.toBoolean(request.getParameter("temp"));
		String imageView2 = request.getQueryString();
		if (imageView2 != null && !imageView2.startsWith("imageView2")) {
			imageView2 = null;
		}

		// Local storage || temp
		if (!QiniuCloud.instance().available() || temp) {
			String fileName = QiniuCloud.parseFileName(filePath);
			String mimeType = request.getServletContext().getMimeType(fileName);
			if (mimeType != null) {
				response.setContentType(mimeType);
			}

			final int wh = imageView2 == null ? 0 : parseWidth(imageView2);

			// 原图
			if (wh <= 0 || wh >= 1000) {
				writeLocalFile(filePath, temp, response);
			}
			// 粗略图
			else {
				filePath = CodecUtils.urlDecode(filePath);
				File img = temp ? SysConfiguration.getFileOfTemp(filePath) : SysConfiguration.getFileOfData(filePath);
				if (!img.exists()) return;

				BufferedImage bi = ImageIO.read(img);
				Thumbnails.Builder<BufferedImage> builder = Thumbnails.of(bi);

				if (bi.getWidth() > wh) {
					builder.size(wh, wh);
				} else {
					builder.scale(1.0);
				}

				builder
						.outputFormat(mimeType != null && mimeType.contains("png") ? "png" : "jpg")
						.toOutputStream(response.getOutputStream());
			}
		}
		else {
			if (imageView2 != null) {
				filePath += "?" + imageView2;
			}

			String privateUrl = QiniuCloud.instance().url(filePath, IMG_CACHE_TIME * 60);
			response.sendRedirect(privateUrl);
		}
	}

	/**
	 * 宽度参数
	 *
	 * @param imageView2
	 * @return
	 */
	private int parseWidth(String imageView2) {
		if (!imageView2.contains("/w/")) {
			return 1000;
		}

		String w = imageView2.split("/w/")[1].split("/")[0];
		return Integer.parseInt(w);
	}
	
	@RequestMapping(value = { "download/**", "access/**" }, method = RequestMethod.GET)
	public void download(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String filePath = request.getRequestURI();

		// 共享查看
		if (request.getRequestURI().contains("/filex/access/")) {
            String e = getParameter(request, "e");
            if (StringUtils.isBlank(e) || Application.getCommonCache().get(e) == null) {
                response.sendError(403, "文件已过期");
                return;
            }

            filePath = filePath.split("/filex/access/")[1];
        } else {
            filePath = filePath.split("/filex/download/")[1];
        }

		boolean temp = BooleanUtils.toBoolean(request.getParameter("temp"));
		String fileName = QiniuCloud.parseFileName(filePath);

		ServletUtils.setNoCacheHeaders(response);

		// Local storage || temp
		if (!QiniuCloud.instance().available() || temp) {
			setDownloadHeaders(request, response, fileName);
			writeLocalFile(filePath, temp, response);
		}
		else {
			String privateUrl = QiniuCloud.instance().url(filePath);
			privateUrl += "&attname=" + fileName;
			response.sendRedirect(privateUrl);
		}
	}

	// --

	/**
	 * 本地文件下载
	 *
	 * @param filePath
	 * @param temp
	 * @param response
	 * @return
	 * @throws IOException
	 */
	public static boolean writeLocalFile(String filePath, boolean temp, HttpServletResponse response) throws IOException {
		filePath = CodecUtils.urlDecode(filePath);
		File file = temp ? SysConfiguration.getFileOfTemp(filePath) : SysConfiguration.getFileOfData(filePath);
		return writeLocalFile(file, response);
	}

	/**
	 * 本地文件下载
	 *
	 * @param file
	 * @param response
	 * @return
	 * @throws IOException
	 */
	public static boolean writeLocalFile(File file, HttpServletResponse response) throws IOException {
        if (!file.exists()) {
            response.setHeader("Content-Disposition", StringUtils.EMPTY);  // Clean download
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return false;
        }

		long size = FileUtils.sizeOf(file);
		response.setHeader("Content-Length", String.valueOf(size));

		try (InputStream fis = new FileInputStream(file)) {
			response.setContentLength(fis.available());

			OutputStream os = response.getOutputStream();
			int count;
			byte[] buffer = new byte[1024 * 1024];
			while ((count = fis.read(buffer)) != -1) {
				os.write(buffer, 0, count);
			}
			os.flush();
			return true;
		}
	}

	/**
	 * 设置下载 Headers
	 *
	 * @param request
	 * @param response
	 * @param attname
	 */
	public static void setDownloadHeaders(HttpServletRequest request, HttpServletResponse response, String attname) {
		// 火狐 Safari 中文名乱码问题
        String UA = StringUtils.defaultIfBlank(request.getHeader("user-agent"), "").toUpperCase();
		if (UA.contains("FIREFOX") || UA.contains("SAFARI")) {
			attname = CodecUtils.urlDecode(attname);
            attname = new String(attname.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        }

		response.setHeader("Content-Disposition", "attachment;filename=" + attname);
		response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
	}
}
