// 总账
import { createMenu, createModel} from '/@/../pages/rbac/utils/mockMenuUtil';

export const platformId = 10133100;

function useId(num) {
  return platformId * 1000 + num
}

function createPlatformMenu(params) {
  return createMenu({...params, platformId})
}


export const menus = [
  createModel({
    id: platformId,
    isCloud: false,
    isTargetBlank: false,
    isOutLink: false,
    name: '操作员',
    category: 101,
    sortNo: 8,
  }),
  createPlatformMenu({
    id: 9901152,
    path: '/',
    redirect: '/log/home/welcome',
    component: 'LAYOUT',
    hidden: true,
    parentId: 0,
    name: '根目录'
  }),
  createPlatformMenu({
    id: useId(300),
    path: '/log/home',
    component: 'LAYOUT',
    parentId: 0,
    name: '工作台'
  }),
  createPlatformMenu({
    id: useId(301),
    path: 'welcome',
    component: '/dashboard/analysis/index',
    parentId: useId(300),
    name: '首页'
  }),
];

export const platformMenuIds = {
  key: 'caoZuoYuan',
  id: platformId,
  sort:9,
  menuIds: []
  // menuIds: menus.map(item => item.id)
};