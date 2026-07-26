package top.srcrs.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 读取本次运行写下的日志，供推送使用。
 *
 * @author srcrs
 * @Time 2020-11-16
 */
@Slf4j
public final class ReadLog {

    private ReadLog() {
    }

    /**
     * 按行读取日志并用指定分隔符拼起来。
     *
     * @param pathName 日志文件路径
     * @param suffix   行之间的分隔符
     * @return 拼好的字符串；读不到时返回提示文案
     */
    public static String getString(String pathName, String suffix) {
        Path path = Paths.get(pathName);
        if (!Files.isReadable(path)) {
            log.warn("⚠️日志文件 {} 不存在，推送内容为空", pathName);
            return "没有读到运行日志";
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            return String.join(suffix, lines);
        } catch (IOException e) {
            log.error("💔读日志文件时出错 : ", e);
            return "读取运行日志失败";
        }
    }

    public static String getMarkDownString(String pathName) {
        return getString(pathName, "\n\n");
    }

    public static String getHTMLString(String pathName) {
        return getString(pathName, "<br />");
    }
}
