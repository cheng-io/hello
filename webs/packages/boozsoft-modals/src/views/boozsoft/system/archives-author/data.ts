import {reactive} from "vue";

const dynamicColumnAndDataModel = reactive({
  DEFAULT: [
    {
      key: '0',
      title: '栏目名称',
      dataIndex: 'name',
      align: 'left',
      width: 150,
    },
    {
      key: '1',
      title: '显示',
      dataIndex: 'check',
      align: 'center',
      slots: { customRender: 'checkBox' },
    },
    {
      key: '3',
      title: '显示名称',
      dataIndex: 'nameNew',
      width: 150,
      align: 'center',
      slots: { customRender: 'nameInput', },
    },
    {
      key: '4',
      title: '宽度(范围)',
      dataIndex: 'width',
      align: 'center',
      width: 140,
      slots: { customRender: 'widthInput', },
    },
    {
      key: '5',
      title: '对齐方式',
      dataIndex: 'align',
      align: 'center',
      width: 140,
      slots: { customRender: 'alignRadio' },
    }
  ],
  DATA: [
    {
      key: '0',
      name: '功能菜单',
      nameNew: '功能菜单',
      check: true,
      width: 150,
      max: 200,
      min: 100,
      isFixed: true,
      align: 'left'
    }
    ,
    {
      key: '1',
      name: '查看',
      nameNew: '查看',
      check: true,
      width: 60,
      max: 100,
      min: 60,
      isFixed: false,
      align: 'center'
    },
    {
      key: '2',
      name: '编辑',
      nameNew: '编辑',
      check: true,
      width: 60,
      max: 100,
      min: 60,
      isFixed: false,
      align: 'center'
    },
    {
      key: '3',
      name: '新增',
      nameNew: '新增',
      check: true,
      width: 60,
      max: 100,
      min: 60,
      isFixed: false,
      align: 'center'
    },
    {
      key: '4',
      name: '修改',
      nameNew: '修改',
      check: true,
      width: 60,
      max: 100,
      min: 60,
      isFixed: false,
      align: 'center'
    },
    {
      key: '5',
      name: '删除',
      nameNew: '删除',
      check: true,
      width: 60,
      max: 100,
      min: 60,
      isFixed: false,
      align: 'center'
    },
    {
      key: '6',
      name: '操作',
      nameNew: '操作',
      check: true,
      width: 60,
      max: 100,
      min: 60,
      isFixed: false,
      align: 'center'
    },
    {
      key: '7',
      name: '插入',
      nameNew: '插入',
      check: true,
      width: 60,
      max: 100,
      min: 60,
      isFixed: false,
      align: 'center'
    },
    {
      key: '8',
      name: '审核/签字',
      nameNew: '审核/签字',
      check: true,
      width: 100,
      max: 120,
      min: 100,
      isFixed: false,
      align: 'center'
    },
    {
      key: '9',
      name: '弃审/取消',
      nameNew: '弃审/取消',
      check: true,
      width: 100,
      max: 120,
      min: 100,
      isFixed: false,
      align: 'center'
    },
    {
      key: '10',
      name: '作废',
      nameNew: '作废',
      check: true,
      width: 60,
      max: 100,
      min: 60,
      isFixed: false,
      align: 'center'
    },
    {
      key: '11',
      name: '生单',
      nameNew: '生单',
      check: true,
      width: 60,
      max: 100,
      min: 60,
      isFixed: false,
      align: 'center'
    },
    {
      key: '12',
      name: '导入',
      nameNew: '导入',
      check: true,
      width: 60,
      max: 100,
      min: 60,
      isFixed: false,
      align: 'center'
    },
    {
      key: '13',
      name: '导出',
      nameNew: '导出',
      check: true,
      width: 60,
      max: 100,
      min: 60,
      isFixed: false,
      align: 'center'
    },
    {
      key: '14',
      name: '打印',
      nameNew: '打印',
      check: true,
      width: 60,
      max: 100,
      min: 60,
      isFixed: false,
      align: 'center'
    },
    {
      key: '15',
      name: '发送邮件',
      nameNew: '发送邮件',
      check: true,
      width: 100,
      max: 120,
      min: 100,
      isFixed: false,
      align: 'center'
    },
    {
      key: '16',
      name: '栏目设置',
      nameNew: '栏目设置',
      check: true,
      width: 100,
      max: 120,
      min: 100,
      isFixed: false,
      align: 'center'
    },
    {
      key: '17',
      name: '启用/生效',
      nameNew: '启用/生效',
      check: true,
      width: 100,
      max: 120,
      min: 100,
      isFixed: false,
      align: 'center'
    },
    {
      key: '18',
      name: '停用/失效',
      nameNew: '停用/失效',
      check: true,
      width: 100,
      max: 120,
      min: 100,
      isFixed: false,
      align: 'center'
    }
  ]
})

export function changeDefaultDynamics(list) {
  // 改变默认数据
  dynamicColumnAndDataModel['DATA'] = list
}

export function initDynamics() {
  return dynamicColumnAndDataModel
}

export function assemblyDynamicColumn(lanmuList:any,columnList:any){
  columnList.forEach(cObj=>{
    lanmuList.forEach((lObj,index)=>{
      if (cObj.title === lObj.name){
        cObj.title = thisName(index+'',lanmuList)
        cObj.width = thisWidth(index+'',lanmuList)
        cObj.align = thisAlign(index+'',lanmuList)
        cObj.ifShow = thisIsShow(index+'',lanmuList)
      }
    })
  })
  return columnList;
}

/*********************** 静态方法 ***********************/
// 动态名称
const thisName = (index,thisData)=>{
  let value = ''
  if (index.toString().indexOf('-') != -1){
    let arr = index.split('-');
    if (arr.length == 2)  {
      value = thisData[parseInt(arr[0])].children[parseInt(arr[1])-1].nameNew
      if ('' == value)value = thisData[parseInt(arr[0])].children[parseInt(arr[1])-1].name
    }else{
      value = thisData[parseInt(arr[0])].children[parseInt(arr[1])-1].children[parseInt(arr[2])-1].nameNew
      if ('' == value)value = thisData[parseInt(arr[0])].children[parseInt(arr[1])-1].children[parseInt(arr[2])-1].name
    }
  }else{
    value = thisData[index].nameNew
    if ('' == value)value = thisData[index].name
  }
  return value
}
// 当前宽度
const thisWidth = (index,thisData)=>{
  let value = 0
  if (index.toString().indexOf('-') != -1){
    let arr = index.split('-');
    if (arr.length == 2)  {
      value = thisData[parseInt(arr[0])].children[parseInt(arr[1])-1].width
    }else{
      value = thisData[parseInt(arr[0])].children[parseInt(arr[1])-1].children[parseInt(arr[2])-1].width
    }
  }else{
    value = thisData[index].width
  }
  return  parseInt(value)
}
// 是否显示
const thisIsShow = (index,thisData)=>{
  let value = false
  if (index.toString().indexOf('-') != -1){
    let arr = index.split('-');
    if (arr.length == 2)  {
      value = thisData[parseInt(arr[0])].children[parseInt(arr[1])-1].check
    }else{
      value = thisData[parseInt(arr[0])].children[parseInt(arr[1])-1].children[parseInt(arr[2])-1].check
    }
  }else{
    value = thisData[index].check
  }
  return  value
}
// 对齐方式
const thisAlign = (index,thisData)=>{
  let value = 'center';
  if (index.toString().indexOf('-') != -1){
    let arr = index.split('-');
    if (arr.length == 2)  {
      value = thisData[parseInt(arr[0])].children[parseInt(arr[1])-1].align
    }else{
      value = thisData[parseInt(arr[0])].children[parseInt(arr[1])-1].children[parseInt(arr[2])-1].align
    }
  }else{
    value = thisData[index].align
  }
  return  value
}
