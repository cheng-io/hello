- 凭证: 
  - 部门辅助核算，只能看到技术部,销售部
  - 人员辅助核算，只能看到张三李四
  - 角色控制范围
    - 技术部会计，相关所有表加一列，range: role_1-6
    - 技术部会计，相关所有表加一列，range: role_1-6





设计表：角色数据范围表
role_range表
role_id  target_table  target_column        target_sql
1000       sys_user         id              in (20,50)
1000       accvoucher   unique_code        like '1001%' and flag=0
1000       sys_dept       dept_id             = '10'
。。。


思考：数据范围表，这个工作量比较大的，调整的地方可能比较多，减少了springdata框架利用，开发效率上会变慢

思路：页面按需开发，页面右上角加个绿字标识【数据范围控制】

总结：
- 数据范围表不适合开发阶段直接做（加重开发逻辑梳理，思路不确定）
- 数据范围表可能更适合迭代项目时去做
- 待功能稳定，【按需渐进】开发数据范围表
- 页面右上角加个绿字标识【数据范围控制】，表示该页面支持数据范围