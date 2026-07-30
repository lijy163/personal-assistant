# Rain7 Blog

独立 Astro 博客前台，视觉结构基于 RyuChan 模板改造，文章数据来自个人系统现有 Spring Boot 接口。

- 公开地址：`/rain7/`
- 文章详情：`/rain7/article?slug=文章路径`
- 内容管理：`/blog/manage`
- 原工作记录博客：`/blog`

创建或编辑文章时选择“Rain7 独立博客”，发布后会实时出现在新站；选择“工作记录博客”则只展示在原博客。

本目录保留原项目 MIT 许可证，参见 `LICENSE`。

## 音乐播放器

播放器支持直接选择浏览器本地音频，文件不会上传。若需部署默认音乐，将自有音频放入 `public/music/`，并在 `public/music/playlist.json` 中配置：

```json
[{"name":"歌曲名","artist":"歌手","url":"/rain7/music/song.mp3"}]
```