## 必读
### 前端资源 & sql
1. 位于others文件夹下，前端端口号为80
2. kill.bat用于杀死全部nginx进程。因为有时会启动多个nginx进程，而`nginx -s stop`只能停止一个进程。

### application.yaml
1. 后端端口号为8080
2. 出于安全考虑，我没有上传application.yaml文件，您只需将application-template.yaml文件重命名为application.yaml并修改mysql和redis配置信息。

## 功能说明
### 短信登录
1. 首先基于session实现短信登录，浏览器的cookie中有一个JSESSIONID，用于唯一标识用户。
2. 因为多个后端服务器并不共享session，所以我使用了redis存储用户信息。
   - 使用String类型存储手机号和验证码；
   - 使用Hash类型存储用户信息，包括id，nickname，icon
3. 通过springboot的拦截器Interceptor实现了拦截未登录用户的请求和刷新redis中用户的过期时间。
    - 记得使用将拦截器配置进spring容器。`@Configuration`注解
### 缓存
1. 使用redis缓存shop信息 get /shop/{id}
2. 使用redis缓存shop列表 get /shop-type/list
3. 商铺数据的redis与sql双写一致。sql数据更新时，删除redis中的数据。redis第一次读取时，会给一个ttl。
4. 如果在根据id查询商户信息时，遇到缓存击穿的情况，就把空值缓存到redis中。
5. 利用逻辑过期解决缓存击穿。即不设置真正的ttl，而是加一个ttl字段，每次查询时判断是否过去，若过期，则获取锁，获得锁成功的线程去fork子线程刷新数据，fork完和获取锁失败的线程均返回旧数据。
6. 封装redis工具类。包括
   1. 将任意对象序列化成json存入redis
   2. 将任意对象序列化成json存入redis 并且携带逻辑过期时间
   3. 设置空值解决缓存穿透
   4. 逻辑过期解决缓存击穿

## 开发过程中遇到的bug
1. 只能拦截/user/code /user/login但无法拦截其他配置请求。
``` java
registry.addInterceptor(new LoginInterceptor())
   .excludePathPatterns(
   "/user/code",
   "/user/login",
   "blog/hot",
   "update/**",
   "shop-type/**",
   "shop/**",
   "voucher/**"
   ).order(1);
```
原因居然是忘记加/了，震惊！