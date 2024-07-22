## 必读
### 前端资源 & sql
1. 位于others文件夹下，前端端口号为80
2. kill.bat用于杀死全部nginx进程。因为有时会启动多个nginx进程，而`nginx -s stop`只能停止一个进程。

### application.yaml
1. 后端端口号为8080
2. 出于安全考虑，我没有上传application.yaml文件，您只需将application-template.yaml文件重命名为application.yaml并修改mysql和redis配置信息。
