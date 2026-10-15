<template>
  <div class="page container">
    <div class="card about">
      <span class="badge">关于我们</span>
      <h1>知行学堂</h1>
      <p class="lead">面向机构与企业的私有化视频学习平台。以「知行合一」为理念，承载资讯、分类课程、点播学习与进度管理，助力组织沉淀可复用的学习资产。</p>
      <div class="grid">
        <div><h3>我们做什么</h3><p>提供 PC 用户端与后台运营系统：课程多级分类、视频点播与续播、学情统计、结业证书，以及 AI 学情分析、知识点、测验与课程问答。</p></div>
        <div><h3>一期能力</h3><p>账号与个人中心、新闻资讯、课程学习、我的学习、访问与学习统计、角色与操作审计。视频本地存储，OSS/CDN 配置预留。业务用户规模约 3 万，按 DID 按 30 万容量设计。</p></div>
        <div><h3>安全与部署</h3><p>支持私有化部署，数据自主可控；接口鉴权、上传校验、进度防刷与操作日志，满足企业内部培训场景。</p></div>
      </div>
      <p class="contact">演示站点：learn.visitworld.me · 如需商务与技术方案请联系项目对接人。</p>
    </div>
  </div>
</template>
<style scoped>
.about { padding: 36px 40px; margin-top: 12px; }
h1 { margin: 10px 0 12px; font-size: 34px; letter-spacing: -.02em; }
.lead { color: var(--ink-600); line-height: 1.7; font-size: 16px; max-width: 760px; }
.grid { display:grid; grid-template-columns: repeat(3,1fr); gap:16px; margin-top:28px; }
.grid h3 { margin:0 0 8px; color: var(--brand-800); }
.grid p { margin:0; color:var(--ink-500); line-height:1.6; font-size:14px; }
.contact { margin-top:28px; color:var(--ink-500); font-size:13px; }
@media (max-width:900px){ .grid{grid-template-columns:1fr} .about{padding:24px} }
</style>
