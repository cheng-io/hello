import {defRouteApi, otherRouteApi, useRouteApi} from "/@/utils/boozsoft/datasource/datasourceUtil";
import {defHttp} from "/@/utils/http/axios";


enum Api {
  findPeriodByCode = '/seachConfig/findAllPeriodByUserAndCode',
  findPeriodByAccontId = '/seachConfig/findPeriodByAccontId',
  findPeriodByAccontIdAndIyaer = '/seachConfig/findPeriodByaccIdAndYear',
  findCodeKmLastByYear = '/seachConfig/findCodeKmLastByYear',
  findCodeKmDept = '/seachConfig/findCodeKmDept',
  findBzAll = '/seachConfig/findBzAll',
  findMaxJc = '/seachConfig/findMaxJc',
  findDeptAll = '/seachConfig/findDeptAll',
  findPsnAll = '/seachConfig/findPsnAll',
  findCusAll = '/seachConfig/findCusAll',
  findSupAll = '/seachConfig/findSupAll',
  findCodeProject = '/seachConfig/findCodeProject',
  findProjectCategory = '/seachConfig/findProjectCategory',
  findProjectClass = '/seachConfig/findProjectClass',
  findProject = '/seachConfig/findProject',
  findMaxQjApi = '/accvoucher/findMaxQj',
  findMaxQjMonthApi = '/accvoucher/findMaxQjMonth',
  exportExcel = '/generalLedger/exportExcel',
  findAll = '/generalLedger/findAll',
  findAllDept = '/generalLedger/findAllDept',
  findAllKmDept = '/generalLedger/findAllKmDept',
  findAllProject = '/generalLedger/findAllProject',
  findAllKmProject = '/generalLedger/findAllKmProject',
  findPeriod = '/seachConfig/findPeriod',
  findPeriodYears = '/seachConfig/findPeriodYears',
  findYearMinDateApi = '/seachConfig/findYearMinDate',
  findYearMaxDateApi = '/seachConfig/findYearMaxDate',
  findAllKmCashFlow = '/seachConfig/findAllKmCashFlow',
  findAllCodeKm = '/seachConfig/findAllCodeKm',
  addKmCashFlow = '/seachConfig/addKmCashFlow',
  findProjectCash = '/cashFolw/findProjectCash',
  saveFp = '/cashFolw/saveFp',
  findXjllMx = '/generalLedger/findXjllMx',
  findCodeKmAll = '/seachConfig/findCodeKmAll',
  findXjllTj = '/generalLedger/findXjllTj',
  findAllMulti = '/generalLedger/findAllKmMulti',
  saveMultiSet = '/settMultiSet/save',
  loadMultiSet = '/settMultiSet/loadKm',
  findCodeByKm = '/generalLedger/findCodeByKm',
  sheetDateOne = '/generalLedger/sheetDateOne',
  sheetDate = '/generalLedger/sheetDate',
  findDeptsheetDate = '/generalLedger/findDeptsheetDate',
  findDeptsheetDateOne = '/generalLedger/findDeptsheetDateOne',
  findByAccId = '/sys/account/findByAccId',
  findAllKmProjectDept = '/generalLedger/findAllKmProjectDept',

}

/**
 * @description: Get user jigou based on id
 */

// ??????????????????
export function findPeriodByAccontId(accontId: any) {
  return defHttp.request({
    url: Api.findPeriodByAccontId + '/' + accontId,
    method: 'GET',
  })
}

// ??????????????????
export function findAllAuthPeriodListByUserAndCode(params) {
  return defHttp.request({
    url: Api.findPeriodByCode,
    method: 'POST', params
  })
}

export function findPeriodByAccontIdAndYaer({accontId, iyear}) {
  return defHttp.request({
    url: Api.findPeriodByAccontIdAndIyaer + '/' + accontId + '/' + iyear,
    method: 'GET',
  })
}

export function findPeriodYears(accontId: any) {
  return defHttp.request({
    url: Api.findPeriodYears + '/' + accontId,
    method: 'GET',
  })
}

export function findYearMinDate(accountId, iyear) {
  return defHttp.request({
    url: Api.findYearMinDateApi + '/' + accountId + '/' + iyear,
    method: 'GET',
  })
}

export function findYearMaxDate(accountId, iyear) {
  return defHttp.request({
    url: Api.findYearMaxDateApi + '/' + accountId + '/' + iyear,
    method: 'GET',
  })
}

// ??????????????????
export function findPeriod(accontId: any) {
  return defHttp.request({
    url: Api.findPeriod + '/' + accontId,
    method: 'GET',
  })
}

// ????????????
export const findCodeKmByPeriod = defRouteApi(async ({strDate, endDate, accId, userId}) => {
  return {
    url:'/seachConfig/findCodeKmByPeriod' + '/' + strDate + '/' + endDate + '/' + accId + '/' + userId,
    method: 'GET',
  };
});

export const findCodeKmLastByYear = defRouteApi(async ({year, accId, userId}) => {
  return {
    url: Api.findCodeKmLastByYear + '/' + year + '/' + accId + '/' + userId,
    method: 'GET',
  };
});
export const findCodeKmAll = defRouteApi(async ({}) => {
  return {
    url: Api.findCodeKmAll,
    method: 'GET',
  };
});
// ???????????????????????????
export const findCodeKmDept = defRouteApi(async ({strDate, endDate, accId, userId}) => {
  return {
    url: Api.findCodeKmDept + '/' + strDate + '/' + endDate + '/' + accId + '/' + userId,
    method: 'GET',
  };
});

// ???????????????????????????
export const findCodeProject = defRouteApi(async ({strDate, endDate, accId, userId}) => {
  return {
    url: Api.findCodeProject + '/' + strDate + '/' + endDate + '/' + accId + '/' + userId,
    method: 'GET',
  };
});

// ??????????????????
export const findBzAll = defRouteApi(async () => {
  return {
    url: Api.findBzAll,
    method: 'GET',
  };
});

// ??????????????????
export function findMaxJc(accontId: any, databaseyear: any) {
  return defHttp.request({
    url: Api.findMaxJc + '/' + accontId + '/' + databaseyear,
    method: 'GET',
  })
}

export const findMaxPingZhengQiJian = defRouteApi(async () => {
  return {
    url: Api.findMaxQjApi,
    method: 'GET',
  };
});
export const findMaxPingZhengQiJianMonth = defRouteApi(async (iyear) => {
  return {
    url: Api.findMaxQjMonthApi + '/' + iyear,
    method: 'GET',
  };
});


//????????????
export const findDeptAll = defRouteApi(async ({}) => {
  return {
    url: Api.findDeptAll,
    method: 'GET',
  };
});

//????????????
export const findProjectCategory = defRouteApi(async ({}) => {
  return {
    url: Api.findProjectCategory,
    method: 'GET',
  };
});

//????????????
export const findProjectClass = defRouteApi(async ({projectCate}) => {
  return {
    url: Api.findProjectClass + '/' + projectCate,
    method: 'GET',
  };
});

//??????
export const findProject = defRouteApi(async ({projectCate}) => {
  return {
    url: Api.findProject + '/' + projectCate,
    method: 'GET',
  };
});

//??????
export const findAll = defRouteApi(async (params) => {
  return {
    url: Api.findAll,
    method: 'POST',
    headers: {
      ignoreCancelToken: true
    },
    params
  }
},)

//????????????
export const findAllDept = defRouteApi((params) => {
  return {
    url: Api.findAllDept,
    method: 'POST',
    headers: {
      ignoreCancelToken: true
    },
    params
  }
})

//??????????????????
export const findAllKmDept = defRouteApi((params) => {
  return {
    url: Api.findAllKmDept,
    method: 'POST',
    headers: {
      ignoreCancelToken: true
    },
    params
  }
})
//????????????
export const findAllProject = defRouteApi((params) => {
  return {
    url: Api.findAllProject,
    method: 'POST',
    headers: {
      ignoreCancelToken: true
    },
    params
  }
})
//??????????????????
export const findAllKmProject = defRouteApi((params) => {
  return {
    url: Api.findAllKmProject,
    method: 'POST',
    headers: {
      ignoreCancelToken: true
    },
    params
  }
})
//??????????????????
export const findAllKmProjectDept = defRouteApi((params) => {
  return {
    url: Api.findAllKmProjectDept,
    method: 'POST',
    headers: {
      ignoreCancelToken: true
    },
    params
  }
})
//???????????????
export const findAllMulti = defRouteApi(async (params) => {
  return {
    url: Api.findAllMulti,
    method: 'POST',
    headers: {
      ignoreCancelToken: true
    },
    params
  }
},)


// ??????????????????
export const findAllCodeKm = defRouteApi(async (params) => {
  return {
    url: Api.findAllCodeKm,
    method: 'POST',
    headers: {
      ignoreCancelToken: true
    },
    params
  };
});

//???????????????????????????????????????
export function findAllKmCashFlow(accId: any) {
  return defHttp.request({
    url: Api.findAllKmCashFlow + '/' + accId,
    method: 'GET',
  })
}

//????????????????????????
export const addKmCashFlow = defRouteApi((params) => {
  return {
    url: Api.addKmCashFlow,
    method: 'POST',
    headers: {
      ignoreCancelToken: true
    },
    params
  }
})
//????????????
export const findPsnAll = defRouteApi(async () => {
  return {
    url: Api.findPsnAll,
    method: 'GET',
  };
});
//??????????????????
export const findCusAll = defRouteApi(async () => {
  return {
    url: Api.findCusAll,
    method: 'GET',
  };
});
//?????????????????????
export const findSupAll = defRouteApi(async () => {
  return {
    url: Api.findSupAll,
    method: 'GET',
  };
});


//????????????????????????
export const findProjectCash = defRouteApi(async ({accId}) => {
  return {
    url: Api.findProjectCash + "/" + accId,
    method: 'GET',
  };
});
//????????????????????????
export const saveFp = defRouteApi(async (params) => {
  return {
    url: Api.saveFp,
    method: 'POST',
    headers: {
      ignoreCancelToken: true
    },
    params
  };
});

//????????????????????????
export const findXjllMx = defRouteApi(async (params) => {
  return {
    url: Api.findXjllMx,
    method: 'POST',
    headers: {
      ignoreCancelToken: true
    },
    params
  };
});
//????????????????????????
export const findXjllTj = defRouteApi(async (params) => {
  return {
    url: Api.findXjllTj,
    method: 'POST',
    headers: {
      ignoreCancelToken: true
    },
    params
  };
});

export const saveMultiSet = defRouteApi(async (params) => {
  return {
    url: Api.saveMultiSet,
    method: 'POST',
    headers: {
      ignoreCancelToken: true
    },
    params
  };
});
export const loadMultiSet = defRouteApi(async ({km}) => {
  return {
    url: Api.loadMultiSet + "/" + km,
    method: 'GET',
  };
});
export const findCodeByKm = defRouteApi(async ({km}) => {
  return {
    url: Api.findCodeByKm + "/" + km,
    method: 'GET',
  };
});

export const sheetDate = defRouteApi(async (params) => {
  return {
    url: Api.sheetDate,
    method: 'POST',
    headers: {
      ignoreCancelToken: true
    },
    params
  }
});
export const sheetDateOne = defRouteApi(async (params) => {
  return {
    url: Api.sheetDateOne,
    method: 'POST',
    headers: {
      ignoreCancelToken: true
    },
    params
  }
});
export const findDeptsheetDate = defRouteApi(async (params) => {
  return {
    url: Api.findDeptsheetDate,
    method: 'POST',
    headers: {
      ignoreCancelToken: true
    },
    params
  }
});

export const findDeptsheetDateOne = defRouteApi(async (params) => {
  return {
    url: Api.findDeptsheetDateOne,
    method: 'POST',
    headers: {
      ignoreCancelToken: true
    },
    params
  }
});

// ??????????????????
export function findByAccId(accontId: any) {
  return defHttp.request({
    url: Api.findByAccId + '/' + accontId,
    method: 'GET',
  })
}
