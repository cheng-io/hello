import {defineStore} from 'pinia';
import {store} from '/@/store';


function strToJson(str) {
  var json = (new Function("return " + str.replaceAll("Printer", '')))();
  return json;
}

export const useNewPrintStore = defineStore({
  id: 'useNewPrintStore',
  state: (): any => ({
      printers:[],
      printBase64:null
  }),
  getters: {
    getPrinters: (state) => state.printers,
    getPrintBase64: (state) => state.printBase64
  },
  actions: {
    setPrintBase64(e){
      this.printBase64=e
    },
    async initPrint(){
     this.printers= strToJson(await window.top.__TAURI_INVOKE__("get_printers_all")).map(it => {
        return {
          label: it.name,
          option: it.name,
          value: it.name
        }
      })
    }
  }
});

// Need to be used outside the setup
export function useNewPrintStoreWidthOut() {
  return useNewPrintStore(store);
}

