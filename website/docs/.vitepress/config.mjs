import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'QueryEngine',
  description: '通过 GraphQL、RESTful、NestQL 接入的查询引擎',
  lang: 'zh-CN',
  appearance: true,
  lastUpdated: true,
  cleanUrls: true,
  themeConfig: {
    siteTitle: 'QueryEngine',
    nav: [
      { text: '开始', link: '/guide/quick-start' },
      { text: 'NestQL', link: '/nestql/' },
      { text: 'HTTP', link: '/http/graphql' },
      {
        text: '0.1.0',
        items: [
          { text: '0.1.0（当前）', link: '/' }
        ]
      }
    ],
    sidebar: [
      {
        text: '开始',
        items: [
          { text: '简介', link: '/' },
          { text: '快速开始', link: '/guide/quick-start' },
          { text: 'Maven 依赖', link: '/guide/maven' },
          { text: '嵌入', link: '/guide/embedding' },
          { text: '扩展与覆盖', link: '/guide/extend' }
        ]
      },
      {
        text: 'NestQL',
        items: [
          { text: '协议', link: '/nestql/' },
          { text: '嵌套示例', link: '/nestql/example' }
        ]
      },
      {
        text: 'HTTP',
        items: [
          { text: 'GraphQL', link: '/http/graphql' },
          { text: 'RESTful', link: '/http/rest' },
          { text: '错误码', link: '/http/errors' }
        ]
      },
      {
        text: '数据',
        items: [
          { text: '注册与 access', link: '/access/' },
          { text: 'MySQL 与达梦', link: '/dialect/' }
        ]
      }
    ],
    search: { provider: 'local' },
    outline: { label: '本页目录', level: [2, 3] },
    darkModeSwitchLabel: '外观',
    lightModeSwitchTitle: '浅色',
    darkModeSwitchTitle: '深色',
    sidebarMenuLabel: '菜单',
    returnToTopLabel: '回到顶部',
    docFooter: { prev: '上一页', next: '下一页' },
    lastUpdatedText: '更新于'
  }
})
