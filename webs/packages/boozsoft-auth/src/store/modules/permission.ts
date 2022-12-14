import type {AppRouteRecordRaw, Menu} from '/@/router/types';
import {findMenus} from '/@/store/modules/boozsoft/findMens';

import {defineStore} from 'pinia';
import {store} from '/@/store';
import {useI18n} from '/@/hooks/web/useI18n';
import {useUserStore} from './user';
import {useAppStoreWidthOut} from './app';
import {toRaw} from 'vue';
import {transformObjToRoute, flatMultiLevelRoutes} from '/@/router/helper/routeHelper';
import {transformRouteToMenu} from '/@/router/helper/menuHelper';

import projectSetting from '/@/settings/projectSetting';

import {PermissionModeEnum} from '/@/enums/appEnum';

import {asyncRoutes, RootRoute} from '/@/router/routes';
import {ERROR_LOG_ROUTE, PAGE_NOT_FOUND_ROUTE} from '/@/router/routes/basic';

import {filter} from '/@/utils/helper/treeHelper';

import {getPermCode} from '/@/api/sys/user';

import {useMessage} from '/@/hooks/web/useMessage';

interface PermissionState {
  // Permission code list
  permCodeList: string[];
  // Whether the route has been dynamically added
  isDynamicAddedRoute: boolean;
  // To trigger a menu update
  lastBuildMenuTime: number;
  // Backstage menu list
  backMenuList: Menu[];
}

export const usePermissionStore = defineStore({
  id: 'app-permission',
  state: (): PermissionState => ({
    permCodeList: [],
    permList: [],
    // Whether the route has been dynamically added
    isDynamicAddedRoute: false,
    // To trigger a menu update
    lastBuildMenuTime: 0,
    // Backstage menu list
    backMenuList: [],
  }),
  getters: {
    getPermCodeList() {
      return this.permCodeList;
    },
    getPermList() {
      return this.permList;
    },
    getBackMenuList() {
      return this.backMenuList;
    },
    getLastBuildMenuTime() {
      return this.lastBuildMenuTime;
    },
    getIsDynamicAddedRoute() {
      return this.isDynamicAddedRoute;
    },
  },
  actions: {
    setPermCodeList(codeList: string[]) {
      this.permList = codeList;
      this.permCodeList = codeList.flatMap(it=>it.perms.split(','));
    },

    setBackMenuList(list: Menu[]) {
      this.backMenuList = list;
      list?.length > 0 && this.setLastBuildMenuTime();
    },

    setLastBuildMenuTime() {
      this.lastBuildMenuTime = new Date().getTime();
    },

    setDynamicAddedRoute(added: boolean) {
      this.isDynamicAddedRoute = added;
    },
    resetState(): void {
      this.isDynamicAddedRoute = false;
      this.permCodeList = [];
      this.backMenuList = [];
      this.lastBuildMenuTime = 0;
    },
    async changePermissionCode() {
      const codeList = await getPermCode();
      this.setPermCodeList(codeList);
    },
    async buildRoutesAction(layoutId): Promise<AppRouteRecordRaw[]> {
      const {t} = useI18n();
      const userStore = useUserStore();
      const appStore = useAppStoreWidthOut();

      let routes: AppRouteRecordRaw[] = [];
      const roleList = toRaw(userStore.getRoleList) || [];
      const {permissionMode = projectSetting.permissionMode} = appStore.getProjectConfig;
      // role permissions
      if (permissionMode === PermissionModeEnum.ROLE) {
        const routeFilter = (route: AppRouteRecordRaw) => {
          const {meta} = route;
          const {roles} = meta || {};
          if (!roles) return true;
          return roleList.some((role) => roles.includes(role));
        };
        routes = filter(asyncRoutes, routeFilter);
        routes = routes.filter(routeFilter);
        // Convert multi-level routing to level 2 routing
        routes = flatMultiLevelRoutes(routes);

        //  If you are sure that you do not need to do background dynamic permissions, please comment the entire judgment below
      } else if (permissionMode === PermissionModeEnum.BACK) {
        const {createMessage} = useMessage();

        // createMessage.loading({
        //   content: t('sys.app.menuLoading'),
        //   duration: 1,
        // });

        // !Simulate to obtain permission codes from the background,
        // this function may only need to be executed once, and the actual project can be put at the right time by itself
        let routeList: AppRouteRecordRaw[] = [];
        try {
          // this.changePermissionCode();
          const menuAll = (await findMenus({layoutId})) as AppRouteRecordRaw[]
          const that=this
          function toMenuPage(menuAll) {
            const funList = []
            const permsList = []

            function toMenuPage2(menuAll) {
              menuAll.forEach(it => {
                if (it.children != null && it.children.length > 0) {
                  toMenuPage2(it.children)
                }
                if (it.category == 2) {
                  funList.push(() => {
                    const index = menuAll.indexOf(it)
                    permsList.push(it)
                    const aaa = menuAll.splice(index, index + 1)
                  })
                }
              })
              return menuAll
            }

            const menuAll2 = toMenuPage2(menuAll)
            funList.forEach(fun => fun())
            that.setPermCodeList(permsList)
            return menuAll2
          }

          routeList = toMenuPage(menuAll);

        } catch (error) {
          console.error(error);
        }

        // Dynamically introduce components
        routeList = transformObjToRoute(routeList);
        //  Background routing to menu structure
        const backMenuList = transformRouteToMenu(routeList);
        this.setBackMenuList(backMenuList);

        routeList = flatMultiLevelRoutes(routeList);

        routes = [PAGE_NOT_FOUND_ROUTE, ...routeList];
      }
      routes.push(ERROR_LOG_ROUTE);
      // routes.push(RootRoute);
      return routes;
    },
  },
});

// Need to be used outside the setup
export function usePermissionStoreWidthOut() {
  return usePermissionStore(store);
}
