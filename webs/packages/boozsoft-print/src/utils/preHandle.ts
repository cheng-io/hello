export function isGoPdf() {
  return !( window.location.href.indexOf('?data') == -1)
}

export async function goPdf() {
  import('/@/components/BoozsoftPdf/viewer');
}

export async function goApp() {
  import('/@/app')
}


export function NcLoader() {
  const funs = {
    thenGoPdf(fun) {
      if (isGoPdf()) {
        fun()
      }
      return funs
    },
    thenGoApp(fun) {
      if (!isGoPdf()) {
        fun()
      }
      return funs
    }
  }
  return funs
}
