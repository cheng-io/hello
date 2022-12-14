let i=0
export function createMenu(params) {
  const { id, parentId, path, name, component,componentName,platformId,perms,permsType,redirect,hidden } = params;
  let category=params.category
  if(category==null){
    category=0
  }
  return {
    id,
    parentId,
    name,
    perms,
    permsType,
    icon: '12312',
    component,
    componentName,
    path,
    redirect,
    sortNo: i++,
    category,
    keepAlive: false,
    description: null,
    createBy: null,
    delFlag: null,
    ruleFlag: null,
    hidden,
    createTime: null,
    updateBy: null,
    updateTime: null,
    status: null,
    alwaysShow: false,
    internalOrExternal: false,
    platformId,
    leaf: false,
    route: false,
  };
}
export const createModel = function ({ id, name, isCloud, isTargetBlank, isOutLink, category, sortNo,path }) {
  return {
    id,
    parentId: 100,
    isCloud,
    name,
    isOutLink,
    isTargetBlank,
    perms: null,
    permsType: null,
    icon: null,
    component: null,
    componentName: null,
    path,
    redirect: null,
    sortNo: null,
    category,
    keepAlive: false,
    description: null,
    createBy: null,
    delFlag: null,
    ruleFlag: null,
    hidden: false,
    createTime: null,
    updateBy: null,
    updateTime: null,
    status: null,
    alwaysShow: false,
    internalOrExternal: false,
    leaf: false,
    route: false,
  };
};

