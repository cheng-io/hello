import './public-path'
import {useUserStoreWidthOut} from '/@/store/modules/user';
import {NcLoader, goPdf, goApp} from '/@/utils/preHandle';

// check api
async function runApp(){
    NcLoader()
      .thenGoPdf(async () => await goPdf())
      // sso返回信息
      // 没登陆登陆
      .thenGoApp(async () => !useUserStoreWidthOut().isSsoBack() && await goApp())
}

runApp()
