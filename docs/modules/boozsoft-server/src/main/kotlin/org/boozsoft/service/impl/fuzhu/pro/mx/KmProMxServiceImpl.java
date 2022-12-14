package org.boozsoft.service.impl.fuzhu.pro.mx;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.boozsoft.domain.entity.codekemu.CodeKemu;
import org.boozsoft.domain.entity.share.project.base.Project;
import org.boozsoft.domain.vo.DeptCodeAccvoucherVo;
import org.boozsoft.domain.vo.KeMuMingXiBigDecimalVo;
import org.boozsoft.domain.vo.ProjectVo;
import org.boozsoft.domain.vo.SubjectInitialBalanceVo;
import org.boozsoft.repo.AccvoucherRepository;
import org.boozsoft.repo.accstyle.AccStyleRepository;
import org.boozsoft.repo.codekemu.CodeKemuRepository;
import org.boozsoft.repo.project.base.ProjectRepositoryBase;
import org.boozsoft.service.impl.SubjectInitialFuZhuBalanceServiceImpl;
import org.boozsoft.util.BigDecimalUtils;
import org.boozsoft.util.addLeftZero;
import org.boozsoft.utils.CollectOfUtils;
import org.springbooz.core.tool.result.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;


@Slf4j
@Service
public class KmProMxServiceImpl {

    @Autowired
    SubjectInitialFuZhuBalanceServiceImpl fuZhuBalanceService;
    @Autowired
    AccvoucherRepository accvoucherRepository;
    @Autowired
    CodeKemuRepository codeKemuRepository;
    @Autowired
    AccStyleRepository accStyleRepository;

    @Autowired
    ProjectRepositoryBase projectRepositoryBase;

    public Mono<R> findAll(Pageable pageable,String dept, String km, String strDate, String endDate, String bz, String ishaveRjz, String styleValue,
                           Map<String, String> searchMap, Map<String, String> filterMap,String database) {
        String year = strDate.substring(0, 4);
        String month = strDate.substring(4, 6);
        AtomicReference<Long> totalAR = new AtomicReference(0);
        // 1?????????????????????????????????
        return projectRepositoryBase.findAllOrderByProCode().collectList()
            .flatMap(deptlist->{
                List<Project> newdeptlist=deptlist;
                if(StringUtils.isNotBlank(dept)){
                    newdeptlist=deptlist.stream().filter(d->d.getUniqueCode().equals(dept)).collect(Collectors.toList());
                }
                List<Project> finalNewdeptlist = newdeptlist;

                // 2 ???????????????????????????????????????
                return accvoucherRepository.findAllByAccVoucherPro(year,km+"%",year+"01",endDate).collectList()
                    .flatMap(accdeptlist->{
                        List<DeptCodeAccvoucherVo> newaccdeptlist=accdeptlist;
                        // ????????????????????????
                        if(!"??????".equals(bz)){
                            newaccdeptlist=accdeptlist.stream().filter(accvd->accvd.getForeignCurrency().equals(bz)).collect(Collectors.toList());
                        }
                        // 3-1???????????????????????????   ???????????????1-5??? ???????????????6-8??? ?????????
                        if("????????????".equals(styleValue)){
                            newaccdeptlist=accdeptlist.stream().filter(ck->Integer.valueOf(ck.getCcode().substring(0,1))<=5).collect(Collectors.toList());
                        }
                        // 3-2???????????????????????????   ???????????????1-5??? ???????????????6-8??? ?????????
                        else if("????????????".equals(styleValue)){
                            newaccdeptlist=accdeptlist.stream().filter(ck->Integer.valueOf(ck.getCcode().substring(0,1))>=6&&Integer.valueOf(ck.getCcode().substring(0,1))<=8).collect(Collectors.toList());
                        }
                        // 3-3???????????????????????????
                        else if(!"??????".equals(styleValue)){
                            newaccdeptlist=accdeptlist.stream().filter(ck->ck.getCclass().equals(styleValue)).collect(Collectors.toList());
                        }

                        // ****** ??????????????????????????????????????? *****????????????????????? ??????????????????
                        if (ishaveRjz.equals("0")) {
                            newaccdeptlist = accdeptlist.stream().filter(vo -> StringUtils.isNotBlank(vo.getIbook()) && vo.getIbook().equals("1")).collect(Collectors.toList());
                        }
                        // ????????????
                        List<DeptCodeAccvoucherVo> finalNewaccdeptlist = newaccdeptlist;
                        return fuZhuBalanceService.findAllSubjectInitialFuZhuBalance(year, "false",year+"21",database)
                                .flatMap(qclist->{
                              Map qcmap = (Map) qclist.getResult();
                             return codeKemuRepository.findByIyearOrderByCcode(year).collectList()
                                .flatMap(deptCodeList->{
                                    List<CodeKemu> newdeptCodeList=deptCodeList.stream().filter(d->d.getCcode().equals(km)).collect(Collectors.toList());
                                    return Mono.just(
                                            CollectOfUtils.mapof("qclist",qcmap.get("tablesData"),"accdeptlist", finalNewaccdeptlist,"deptlist", finalNewdeptlist,"deptCodeList",newdeptCodeList.get(0))
                                    );
                                });
                          });
                    });
            })
            .flatMap(map->{
                List<KeMuMingXiBigDecimalVo> mapList = new ArrayList<>();
                List<SubjectInitialBalanceVo> qclist = (List<SubjectInitialBalanceVo>) map.get("qclist");
                List<DeptCodeAccvoucherVo> pzlist = (List<DeptCodeAccvoucherVo>) map.get("accdeptlist");
                List<Project> deptlist = (List<Project>) map.get("deptlist");
                CodeKemu codeinfo= (CodeKemu) map.get("deptCodeList");  // ????????????

                int number = 0; // ??????
                AtomicReference<BigDecimal> lastYue= new AtomicReference<>(new BigDecimal(0));
                AtomicReference<BigDecimal> lastYue_num= new AtomicReference<>(new BigDecimal(0));
                AtomicReference<BigDecimal> lastYue_nfrat= new AtomicReference<>(new BigDecimal(0));
                deptlist.stream().forEach(d->{
                    BigDecimal codeTotalMd=new BigDecimal(0);
                    BigDecimal codeTotalMc=new BigDecimal(0);
                    BigDecimal codeTotalNds=new BigDecimal(0);
                    BigDecimal codeTotalNcs=new BigDecimal(0);
                    BigDecimal codeTotalNfratMd=new BigDecimal(0);
                    BigDecimal codeTotalNfratMc=new BigDecimal(0);
                    // ?????????????????????
                    String unitMeasurement=codeinfo.getMenterage();
                    // ?????????????????????
                    String foreignCurrency =codeinfo.getCurrencyType();
                    // ************* ?????????????????? *************
                    List<SubjectInitialBalanceVo> findByCodeQcList = qclist.stream().filter(qc ->StrUtil.isNotBlank(qc.getProjectId()) && qc.getCcode().equals(km) && qc.getProjectId().equals(d.getUniqueCode()) ).collect(Collectors.toList());
                    BigDecimal qcmd=findByCodeQcList.size()>0?findByCodeQcList.get(0).getMd():new BigDecimal(0);
                    BigDecimal qcmc=findByCodeQcList.size()>0?findByCodeQcList.get(0).getMc():new BigDecimal(0);
                    BigDecimal qcndS=findByCodeQcList.size()>0?findByCodeQcList.get(0).getNdS():new BigDecimal(0);
                    BigDecimal qcncS=findByCodeQcList.size()>0?findByCodeQcList.get(0).getNcS():new BigDecimal(0);
                    BigDecimal qcnfratMd=findByCodeQcList.size()>0?findByCodeQcList.get(0).getNfratMd():new BigDecimal(0);
                    BigDecimal qcnfratMc=findByCodeQcList.size()>0?findByCodeQcList.get(0).getNfratMc():new BigDecimal(0);
                    BigDecimal yue=new BigDecimal(0);
                    BigDecimal yue_num=new BigDecimal(0);
                    BigDecimal yue_nfrat=new BigDecimal(0);

                    BigDecimal lastmd=new BigDecimal(0);
                    BigDecimal lastmc=new BigDecimal(0);
                    BigDecimal lastndS=new BigDecimal(0);
                    BigDecimal lastncS=new BigDecimal(0);
                    BigDecimal lastnfratMd=new BigDecimal(0);
                    BigDecimal lastnfratMc=new BigDecimal(0);

                    // ??????????????? ???????????????????????? ??????????????????????????????
                    if (Integer.valueOf(strDate.substring(4, 6)) > 1) {
                        String lastmonth = Integer.valueOf(month) - 1 < 10 ? "0" + (Integer.valueOf(month) - 1) : month;
                        // ??????????????????????????????
                        List<DeptCodeAccvoucherVo> lastMonthAccVoucher = pzlist.stream().filter(acv -> Integer.valueOf(acv.getImonth()) <= Integer.valueOf(lastmonth) && acv.getCcode().equals(codeinfo.getCcode()) && acv.getProjectId().equals(d.getUniqueCode())).collect(Collectors.toList());
                        lastmd=lastMonthAccVoucher.size()>0?lastMonthAccVoucher.stream().map(DeptCodeAccvoucherVo::getMd).reduce(BigDecimal.ZERO, BigDecimalUtils::sum):new BigDecimal(0);
                        lastmc=lastMonthAccVoucher.size()>0?lastMonthAccVoucher.stream().map(DeptCodeAccvoucherVo::getMc).reduce(BigDecimal.ZERO, BigDecimalUtils::sum):new BigDecimal(0);
                        lastndS=lastMonthAccVoucher.size()>0?lastMonthAccVoucher.stream().map(DeptCodeAccvoucherVo::getNdS).reduce(BigDecimal.ZERO, BigDecimalUtils::sum):new BigDecimal(0);
                        lastncS=lastMonthAccVoucher.size()>0?lastMonthAccVoucher.stream().map(DeptCodeAccvoucherVo::getNcS).reduce(BigDecimal.ZERO, BigDecimalUtils::sum):new BigDecimal(0);
                        lastnfratMd=lastMonthAccVoucher.size()>0?lastMonthAccVoucher.stream().map(DeptCodeAccvoucherVo::getNfratMd).reduce(BigDecimal.ZERO, BigDecimalUtils::sum):new BigDecimal(0);
                        lastnfratMc=lastMonthAccVoucher.size()>0?lastMonthAccVoucher.stream().map(DeptCodeAccvoucherVo::getNfratMc).reduce(BigDecimal.ZERO, BigDecimalUtils::sum):new BigDecimal(0);
                    }

                    String qc_bprogerty="";
                    // ??????
                    if(codeinfo.getBprogerty().equals("1")){
                        yue=qcmd.add(lastmd).subtract(qcmc.add(lastmc));
                        yue_num=qcndS.add(lastndS).subtract(qcncS.add(lastncS));
                        yue_nfrat=qcnfratMd.add(lastnfratMd).subtract(qcnfratMc.add(lastnfratMc));
                        if(yue.compareTo(BigDecimal.ZERO) == 0){
                            qc_bprogerty="???";
                        }else if(yue.compareTo(BigDecimal.ZERO)>0){
                            qc_bprogerty="???";
                        }else{
                            qc_bprogerty="???";
                        }
                    }else{
                        yue=qcmc.add(lastmc).subtract(qcmd.add(lastmd));
                        yue_num=qcncS.add(lastncS).subtract(qcndS.add(lastndS));
                        yue_nfrat=qcnfratMc.add(lastnfratMc).subtract(qcnfratMd.add(lastnfratMd));

                        if(yue.compareTo(BigDecimal.ZERO) == 0){
                            qc_bprogerty="???";
                        }else if(yue.compareTo(BigDecimal.ZERO)>0){
                            qc_bprogerty="???";
                        }else{
                            qc_bprogerty="???";
                        }
                    }
                    if(yue.compareTo(BigDecimal.ZERO)!=0){
                        mapList.add(
                                new KeMuMingXiBigDecimalVo()
                                        .setNumber(number)
                                        .setInoIdASC(0)
                                        .setCcode(d.getProjectCode())
                                        .setCcodeName(d.getProjectName())
                                        .setCdigest("????????????")
                                        .setBprogerty(qc_bprogerty)
                                        .setYue(yue.compareTo(BigDecimal.ZERO) < 0?yue.multiply(new BigDecimal(-1)):yue)
                                        .setTempyue(yue)
                                        .setNcnum(yue_num.compareTo(BigDecimal.ZERO) < 0?yue_num.multiply(new BigDecimal(-1)):yue_num)
                                        .setYue_num(yue_num.compareTo(BigDecimal.ZERO) < 0?yue_num.multiply(new BigDecimal(-1)):yue_num)
                                        .setYue_nfrat(yue_nfrat.compareTo(BigDecimal.ZERO) < 0?yue_nfrat.multiply(new BigDecimal(-1)):yue_nfrat)
                        );
                    }

                    // ************* ???????????????????????? *************
                    List<DeptCodeAccvoucherVo> collect = pzlist.stream().filter(pz -> Integer.valueOf(pz.getIyperiod())>=Integer.valueOf(strDate) && Integer.valueOf(pz.getIyperiod())<=Integer.valueOf(endDate) && pz.getProjectId().equals(d.getUniqueCode())).collect(Collectors.toList());
                    for (int i = 0; i < collect.size(); i++) {
                        BigDecimal pzmd=collect.size()>0?collect.get(i).getMd():new BigDecimal(0);
                        BigDecimal pzmc=collect.size()>0?collect.get(i).getMc():new BigDecimal(0);
                        BigDecimal pzndS=collect.size()>0?collect.get(i).getNdS():new BigDecimal(0);
                        BigDecimal pzncS=collect.size()>0?collect.get(i).getNcS():new BigDecimal(0);
                        BigDecimal pznfratMd=collect.size()>0?collect.get(i).getNfratMd():new BigDecimal(0);
                        BigDecimal pznfratMc=collect.size()>0?collect.get(i).getNfratMc():new BigDecimal(0);

                        // ??????
                        codeTotalMd=codeTotalMd.add(pzmd);
                        codeTotalMc=codeTotalMc.add(pzmc);
                        codeTotalNds=codeTotalNds.add(pzndS);
                        codeTotalNcs=codeTotalNcs.add(pzncS);
                        codeTotalNfratMd=codeTotalNfratMd.add(pznfratMd);
                        codeTotalNfratMc=codeTotalNfratMc.add(pznfratMc);

                        String bprogerty="";
                        if(codeinfo.getBprogerty().equals("1")){
                            yue=yue.add(pzmd).subtract(pzmc);
                            yue_num=yue_num.add(pzndS).subtract(pzncS);
                            yue_nfrat.add(yue_nfrat).subtract(pznfratMc);

                            if(yue.compareTo(BigDecimal.ZERO) == 0){
                                bprogerty="???";
                            }else if(yue.compareTo(BigDecimal.ZERO)>0){
                                bprogerty="???";
                            }else{
                                bprogerty="???";
                            }

                        }else{
                            yue=yue.add(pzmc).subtract(pzmd);
                            yue_num=yue_num.add(pzncS).subtract(pzndS);
                            yue_nfrat=yue_nfrat.add(pznfratMc).subtract(pznfratMd);

                            if(yue.compareTo(BigDecimal.ZERO) == 0){
                                bprogerty="???";
                            }else if(yue.compareTo(BigDecimal.ZERO)>0){
                                bprogerty="???";
                            }else{
                                bprogerty="???";
                            }
                        }
                        BigDecimal tempYue=yue.compareTo(BigDecimal.ZERO) < 0 ? yue.multiply(new BigDecimal(-1)) : yue;
                        BigDecimal tempYueNfract=yue_nfrat.compareTo(BigDecimal.ZERO) > 0 ? yue_nfrat : yue_nfrat.multiply(new BigDecimal(-1));

                        if(pzmd.compareTo(BigDecimal.ZERO)!=0 || pzmc.compareTo(BigDecimal.ZERO)!=0) {
                            KeMuMingXiBigDecimalVo mx=new KeMuMingXiBigDecimalVo();
                            mx.setNumber(i+1)
                                    .setCcode(d.getProjectCode())
                                    .setCcodeName(d.getProjectName())
                                    .setDbillDate(collect.get(i).getDbillDate())
                                    .setInoIdASC(Integer.valueOf(collect.get(i).getInoId()))
                                    .setInoId(collect.get(i).getCsign() + "-" + addLeftZero.addZeroForNum(collect.get(i).getInoId(), 4))
                                    .setCdigest(collect.get(i).getCdigest())
                                    .setMd(pzmd)
                                    .setMc(pzmc)
                                    .setBprogerty(bprogerty)
                                    .setNdS(pzndS)
                                    .setNcS(pzncS)
                                    .setCunitPrice(new BigDecimal(collect.get(i).getCunitPrice()))
                                    .setUnitMeasurement(unitMeasurement)
                                    .setMdF(new BigDecimal(collect.get(i).getMdF()))
                                    .setNfrat_md(pznfratMd)
                                    .setNfrat_mc(pznfratMc)
                                    .setForeignCurrency(foreignCurrency)
                                    .setYue(tempYue)
                                    .setYue_num(yue_num)
                                    .setYue_nfrat(tempYueNfract);

                            mapList.add(mx);
                            lastYue.set(tempYue);
                            lastYue_num.set(yue_num);
                            lastYue_nfrat.set(tempYueNfract);
                        }
                    }
                    if(codeTotalMd.compareTo(BigDecimal.ZERO)!=0 || codeTotalMc.compareTo(BigDecimal.ZERO)!=0) {
                        mapList.add(new KeMuMingXiBigDecimalVo()
                                .setNumber(number+1)
                                .setInoIdASC(0)
                                .setCdigest("????????????")
                                .setCcode(d.getProjectCode())
                                .setCcodeName(d.getProjectName())
                                .setMd(codeTotalMd)
                                .setMc(codeTotalMc)
                                .setNdS(codeTotalNds)
                                .setNcS(codeTotalNcs)
                                .setBprogerty(lastYue.get().compareTo(BigDecimal.ZERO) == 0 ? "???" : lastYue.get().compareTo(BigDecimal.ZERO) > 0 ? "???" : "???")
                                .setNfrat_md(codeTotalNfratMd)
                                .setNfrat_mc(codeTotalNfratMc)
                                .setYue(lastYue.get().compareTo(BigDecimal.ZERO) > 0 ? lastYue.get() : lastYue.get().multiply(new BigDecimal(-1)))
                                .setYue_num(lastYue_num.get())
                                .setYue_nfrat(lastYue_nfrat.get())
                        );
                    }
                });
                // ??????
                BigDecimal totalMd=mapList.stream().filter(f->f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getMd).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                BigDecimal totalMc=mapList.stream().filter(f->f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getMc).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                BigDecimal totalNds=mapList.stream().filter(f->f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getNdS).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                BigDecimal totalNcs=mapList.stream().filter(f->f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getNcS).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                BigDecimal totalNfratMd=mapList.stream().filter(f->f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getNfrat_md).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                BigDecimal totalNfratMc=mapList.stream().filter(f->f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getNfrat_mc).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);

                BigDecimal totalyuenum=mapList.stream().filter(f->f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getYue_num).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                BigDecimal totalyuenf=mapList.stream().filter(f->f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getYue_nfrat).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);

                BigDecimal total_qcyuemd=mapList.stream().filter(f->f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getTempyue).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                String bprogerty=codeinfo.getBprogerty().equals("1") ? "???" : "???";
                BigDecimal totalyue=new BigDecimal(0);
                if(bprogerty.equals("???")){
                    BigDecimal qm=total_qcyuemd;
                    BigDecimal qc=totalMd.subtract(totalMc);
                    BigDecimal temp=qm.add(qc);
                    totalyue=temp.compareTo(BigDecimal.ZERO)<0?temp.multiply(new BigDecimal(-1)):temp;
                }else{
                    BigDecimal qm=total_qcyuemd;
                    BigDecimal qc=totalMc.subtract(totalMd);
                    BigDecimal temp=qm.add(qc);
                    totalyue=temp.compareTo(BigDecimal.ZERO)<0?temp.multiply(new BigDecimal(-1)):temp;
                }

                if(totalMd.compareTo(BigDecimal.ZERO)!=0 || totalMc.compareTo(BigDecimal.ZERO)!=0) {
                    mapList.add(new KeMuMingXiBigDecimalVo()
                            .setNumber(number)
                            .setInoIdASC(0)
                            .setCdigest("??????")
                            .setBprogerty(bprogerty)
                            .setMd(totalMd)
                            .setMc(totalMc)
                            .setNdS(totalNds)
                            .setNcS(totalNcs)
                            .setYue(totalyue)
                            .setYue_num(totalyuenum)
                            .setYue_nfrat(totalyuenf)
                            .setNfrat_md(totalNfratMd)
                            .setNfrat_mc(totalNfratMc)
                    );
                }
                totalAR.set((long) mapList.size());
                return Mono.just(
                            mapList.stream().filter(item->{
                                // ???????????????
                                if (org.springbooz.core.tool.utils.StringUtils.isNotBlank(searchMap.get("requirement")) && org.springbooz.core.tool.utils.StringUtils.isNotBlank(searchMap.get("value"))) {
                                    String value = searchMap.get("value");
                                    if (searchMap.get("requirement").trim().equals("bprogerty")) {
                                        if (!item.getBprogerty().contains(value) && !item.getBprogerty().contains(value)) {
                                            return false;
                                        }
                                    } else {
                                        String dbValue = getFieldValueByFieldName(searchMap.get("requirement").trim(), item);
                                        if (Objects.nonNull(dbValue) && !dbValue.contains(value)) {
                                            return false;
                                        }
                                    }
                                }

                                // ????????????-??????
                                if (org.springbooz.core.tool.utils.StringUtils.isNotBlank(filterMap.get("amountMinJf")) && org.springbooz.core.tool.utils.StringUtils.isNotBlank(filterMap.get("amountMaxJf"))) {
                                    BigDecimal min = new BigDecimal(filterMap.get("amountMinJf"));
                                    BigDecimal max = new BigDecimal(filterMap.get("amountMaxJf"));
                                    BigDecimal s_md = new BigDecimal(item.getMd().toString().replaceAll(",",""));
                                    if (s_md.compareTo(min) < 0 || s_md.compareTo(max) > 0) {
                                        return false;
                                    }
                                }
                                // ????????????-??????
                                if (org.springbooz.core.tool.utils.StringUtils.isNotBlank(filterMap.get("amountMinDf")) && org.springbooz.core.tool.utils.StringUtils.isNotBlank(filterMap.get("amountMaxDf"))) {
                                    BigDecimal min = new BigDecimal(filterMap.get("amountMinDf"));
                                    BigDecimal max = new BigDecimal(filterMap.get("amountMaxDf"));
                                    BigDecimal s_mc = new BigDecimal(item.getMc().toString().replaceAll(",",""));
                                    if (s_mc.compareTo(min) < 0 || s_mc.compareTo(max) > 0) {
                                        return false;
                                    }
                                }
                                // ????????????-??????
                                if (StrUtil.isNotBlank(filterMap.get("amountMinYe")) && StrUtil.isNotBlank(filterMap.get("amountMaxYe"))) {
                                    BigDecimal min = new BigDecimal(filterMap.get("amountMinYe"));
                                    BigDecimal max = new BigDecimal(filterMap.get("amountMaxYe"));
                                    BigDecimal s_yue = new BigDecimal(item.getYue().toString().replaceAll(",",""));
                                    //????????????
                                    if (s_yue.compareTo(min) < 0 || s_yue.compareTo(max) > 0) {
                                        return false;
                                    }
                                }
                                return true;
                            })
                );
            })
            .map(a -> R.page(a.collect(Collectors.toList()),pageable,(totalAR.get())));

    }
    private String getFieldValueByFieldName(String fieldName, Object object) {
        try {
            String firstLetter = fieldName.substring(0, 1).toUpperCase();
            String getter = "get" + firstLetter + fieldName.substring(1);
            Method method = object.getClass().getMethod(getter, new Class[]{});
            Object value = method.invoke(object, new Object[]{});
            return value.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public Mono<R> exportAll(String strDate, String endDate, String bz, String ishaveRjz, String styleValue,List<String> ccodeList) {
        String year = strDate.substring(0, 4);
        String month = strDate.substring(4, 6);
        AtomicReference<Long> totalAR = new AtomicReference(0);
        // 1?????????????????????????????????
        return projectRepositoryBase.findAllOrderByProCode2().collectList()
            .flatMap(prolist->{
                List<ProjectVo> finalNewdeptlist = prolist;

                // 2 ???????????????????????????????????????
                return accvoucherRepository.findAllByIyearFuzhuAccvoucher(year,year+"01",endDate).collectList()
                        .flatMap(accdeptlist->{
                            List<DeptCodeAccvoucherVo> newaccdeptlist=accdeptlist;
                            // ????????????????????????
                            if(!"??????".equals(bz)){
                                newaccdeptlist=accdeptlist.stream().filter(accvd->accvd.getForeignCurrency().equals(bz)).collect(Collectors.toList());
                            }
                            // 3-1???????????????????????????   ???????????????1-5??? ???????????????6-8??? ?????????
                            if("????????????".equals(styleValue)){
                                newaccdeptlist=accdeptlist.stream().filter(ck->Integer.valueOf(ck.getCcode().substring(0,1))<=5).collect(Collectors.toList());
                            }
                            // 3-2???????????????????????????   ???????????????1-5??? ???????????????6-8??? ?????????
                            else if("????????????".equals(styleValue)){
                                newaccdeptlist=accdeptlist.stream().filter(ck->Integer.valueOf(ck.getCcode().substring(0,1))>=6&&Integer.valueOf(ck.getCcode().substring(0,1))<=8).collect(Collectors.toList());
                            }
                            // 3-3???????????????????????????
                            else if(!"??????".equals(styleValue)){
                                newaccdeptlist=accdeptlist.stream().filter(ck->ck.getCclass().equals(styleValue)).collect(Collectors.toList());
                            }

                            // ****** ??????????????????????????????????????? *****????????????????????? ??????????????????
                            if (ishaveRjz.equals("0")) {
                                newaccdeptlist = accdeptlist.stream().filter(vo -> StringUtils.isNotBlank(vo.getIbook()) && vo.getIbook().equals("1")).collect(Collectors.toList());
                            }
                            // ????????????
                            List<DeptCodeAccvoucherVo> finalNewaccdeptlist = newaccdeptlist;
                            return fuZhuBalanceService.findAllSubjectInitialFuZhuBalance(year, "false",year+"21","")
                                    .flatMap(qclist->{
                                        Map qcmap = (Map) qclist.getResult();
                                        return codeKemuRepository.findByIyearOrderByCcode(year).collectList()
                                                .flatMap(codelist->{
                                                    return Mono.just(
                                                            CollectOfUtils.mapof("qclist",qcmap.get("tablesData"),"accdeptlist",
                                                                    finalNewaccdeptlist,"deptlist", finalNewdeptlist,"codelist",codelist)
                                                    );
                                                });
                                    });
                        });
            })
            .flatMap(map->{
                List<SubjectInitialBalanceVo> qclist = (List<SubjectInitialBalanceVo>) map.get("qclist");
                List<DeptCodeAccvoucherVo> pzlist = (List<DeptCodeAccvoucherVo>) map.get("accdeptlist");
                List<ProjectVo> deptlist = (List<ProjectVo>) map.get("deptlist");
                List<CodeKemu> ccodelist= (List<CodeKemu>) map.get("codelist");  // ????????????
                // ??????list
                List<Map<String,Object>> excellist = new ArrayList<>();
                ccodeList.stream().forEach(km->{
                    List<KeMuMingXiBigDecimalVo> mapList = new ArrayList<>();
                    CodeKemu codeinfo = ccodelist.stream().filter(v -> v.getCcode().equals(km)).collect(Collectors.toList()).get(0);

                    AtomicReference<BigDecimal> lastYue= new AtomicReference<>(new BigDecimal(0));
                    AtomicReference<BigDecimal> lastYue_num= new AtomicReference<>(new BigDecimal(0));
                    AtomicReference<BigDecimal> lastYue_nfrat= new AtomicReference<>(new BigDecimal(0));

                    List<ProjectVo> proVolist = deptlist.stream().filter(v -> v.getCcode().equals(km)).collect(Collectors.toList());
                    proVolist.stream().forEach(d->{
                        BigDecimal codeTotalMd=new BigDecimal(0);
                        BigDecimal codeTotalMc=new BigDecimal(0);
                        BigDecimal codeTotalNds=new BigDecimal(0);
                        BigDecimal codeTotalNcs=new BigDecimal(0);
                        BigDecimal codeTotalNfratMd=new BigDecimal(0);
                        BigDecimal codeTotalNfratMc=new BigDecimal(0);
                        // ?????????????????????
                        String unitMeasurement=codeinfo.getMenterage();
                        // ?????????????????????
                        String foreignCurrency =codeinfo.getCurrencyType();
                        // ************* ?????????????????? *************
                        List<SubjectInitialBalanceVo> findByCodeQcList = qclist.stream().filter(qc ->StrUtil.isNotBlank(qc.getProjectId()) && qc.getCcode().equals(km) && qc.getProjectId().equals(d.getUniqueCode()) ).collect(Collectors.toList());
                        BigDecimal qcmd=findByCodeQcList.size()>0?findByCodeQcList.get(0).getMd():new BigDecimal(0);
                        BigDecimal qcmc=findByCodeQcList.size()>0?findByCodeQcList.get(0).getMc():new BigDecimal(0);
                        BigDecimal qcndS=findByCodeQcList.size()>0?findByCodeQcList.get(0).getNdS():new BigDecimal(0);
                        BigDecimal qcncS=findByCodeQcList.size()>0?findByCodeQcList.get(0).getNcS():new BigDecimal(0);
                        BigDecimal qcnfratMd=findByCodeQcList.size()>0?findByCodeQcList.get(0).getNfratMd():new BigDecimal(0);
                        BigDecimal qcnfratMc=findByCodeQcList.size()>0?findByCodeQcList.get(0).getNfratMc():new BigDecimal(0);
                        BigDecimal yue=new BigDecimal(0);
                        BigDecimal yue_num=new BigDecimal(0);
                        BigDecimal yue_nfrat=new BigDecimal(0);

                        BigDecimal lastmd=new BigDecimal(0);
                        BigDecimal lastmc=new BigDecimal(0);
                        BigDecimal lastndS=new BigDecimal(0);
                        BigDecimal lastncS=new BigDecimal(0);
                        BigDecimal lastnfratMd=new BigDecimal(0);
                        BigDecimal lastnfratMc=new BigDecimal(0);

                        // ??????????????? ???????????????????????? ??????????????????????????????
                        if (Integer.valueOf(strDate.substring(4, 6)) > 1) {
                            String lastmonth = Integer.valueOf(month) - 1 < 10 ? "0" + (Integer.valueOf(month) - 1) : month;
                            // ??????????????????????????????
                            List<DeptCodeAccvoucherVo> lastMonthAccVoucher = pzlist.stream().filter(acv -> Integer.valueOf(acv.getImonth()) <= Integer.valueOf(lastmonth) && acv.getCcode().equals(codeinfo.getCcode()) && acv.getProjectId().equals(d.getUniqueCode())).collect(Collectors.toList());
                            lastmd=lastMonthAccVoucher.size()>0?lastMonthAccVoucher.stream().map(DeptCodeAccvoucherVo::getMd).reduce(BigDecimal.ZERO, BigDecimalUtils::sum):new BigDecimal(0);
                            lastmc=lastMonthAccVoucher.size()>0?lastMonthAccVoucher.stream().map(DeptCodeAccvoucherVo::getMc).reduce(BigDecimal.ZERO, BigDecimalUtils::sum):new BigDecimal(0);
                            lastndS=lastMonthAccVoucher.size()>0?lastMonthAccVoucher.stream().map(DeptCodeAccvoucherVo::getNdS).reduce(BigDecimal.ZERO, BigDecimalUtils::sum):new BigDecimal(0);
                            lastncS=lastMonthAccVoucher.size()>0?lastMonthAccVoucher.stream().map(DeptCodeAccvoucherVo::getNcS).reduce(BigDecimal.ZERO, BigDecimalUtils::sum):new BigDecimal(0);
                            lastnfratMd=lastMonthAccVoucher.size()>0?lastMonthAccVoucher.stream().map(DeptCodeAccvoucherVo::getNfratMd).reduce(BigDecimal.ZERO, BigDecimalUtils::sum):new BigDecimal(0);
                            lastnfratMc=lastMonthAccVoucher.size()>0?lastMonthAccVoucher.stream().map(DeptCodeAccvoucherVo::getNfratMc).reduce(BigDecimal.ZERO, BigDecimalUtils::sum):new BigDecimal(0);
                        }

                        String qc_bprogerty="";
                        // ??????
                        if(codeinfo.getBprogerty().equals("1")){
                            yue=qcmd.add(lastmd).subtract(qcmc.add(lastmc));
                            yue_num=qcndS.add(lastndS).subtract(qcncS.add(lastncS));
                            yue_nfrat=qcnfratMd.add(lastnfratMd).subtract(qcnfratMc.add(lastnfratMc));
                            if(yue.compareTo(BigDecimal.ZERO) == 0){
                                qc_bprogerty="???";
                            }else if(yue.compareTo(BigDecimal.ZERO)>0){
                                qc_bprogerty="???";
                            }else{
                                qc_bprogerty="???";
                            }
                        }else{
                            yue=qcmc.add(lastmc).subtract(qcmd.add(lastmd));
                            yue_num=qcncS.add(lastncS).subtract(qcndS.add(lastndS));
                            yue_nfrat=qcnfratMc.add(lastnfratMc).subtract(qcnfratMd.add(lastnfratMd));

                            if(yue.compareTo(BigDecimal.ZERO) == 0){
                                qc_bprogerty="???";
                            }else if(yue.compareTo(BigDecimal.ZERO)>0){
                                qc_bprogerty="???";
                            }else{
                                qc_bprogerty="???";
                            }
                        }
                        if(yue.compareTo(BigDecimal.ZERO)!=0){
                            mapList.add(
                                new KeMuMingXiBigDecimalVo()
                                    .setInoIdASC(0)
                                    .setCcode(d.getProjectCode())
                                    .setCcodeName(d.getProjectName())
                                    .setCdigest("????????????")
                                    .setBprogerty(qc_bprogerty)
                                    .setYue(yue.compareTo(BigDecimal.ZERO) < 0?yue.multiply(new BigDecimal(-1)):yue)
                                    .setTempyue(yue)
                                    .setNcnum(yue_num.compareTo(BigDecimal.ZERO) < 0?yue_num.multiply(new BigDecimal(-1)):yue_num)
                                    .setYue_num(yue_num.compareTo(BigDecimal.ZERO) < 0?yue_num.multiply(new BigDecimal(-1)):yue_num)
                                    .setYue_nfrat(yue_nfrat.compareTo(BigDecimal.ZERO) < 0?yue_nfrat.multiply(new BigDecimal(-1)):yue_nfrat)
                                    .setTemp1(codeinfo.getCcode())
                                    .setTemp2(codeinfo.getCcodeName())
                            );
                        }

                        // ************* ???????????????????????? *************
                        List<DeptCodeAccvoucherVo> collect = pzlist.stream().filter(pz ->StrUtil.isNotBlank(pz.getProjectId())&& pz.getProjectId().equals(d.getUniqueCode())&&pz.getCcode().equals(km) && Integer.valueOf(pz.getIyperiod())>=Integer.valueOf(strDate) && Integer.valueOf(pz.getIyperiod())<=Integer.valueOf(endDate)).collect(Collectors.toList());
                        for (int i = 0; i < collect.size(); i++) {
                            BigDecimal pzmd=collect.size()>0?collect.get(i).getMd():new BigDecimal(0);
                            BigDecimal pzmc=collect.size()>0?collect.get(i).getMc():new BigDecimal(0);
                            BigDecimal pzndS=collect.size()>0?collect.get(i).getNdS():new BigDecimal(0);
                            BigDecimal pzncS=collect.size()>0?collect.get(i).getNcS():new BigDecimal(0);
                            BigDecimal pznfratMd=collect.size()>0?collect.get(i).getNfratMd():new BigDecimal(0);
                            BigDecimal pznfratMc=collect.size()>0?collect.get(i).getNfratMc():new BigDecimal(0);

                            // ??????
                            codeTotalMd=codeTotalMd.add(pzmd);
                            codeTotalMc=codeTotalMc.add(pzmc);
                            codeTotalNds=codeTotalNds.add(pzndS);
                            codeTotalNcs=codeTotalNcs.add(pzncS);
                            codeTotalNfratMd=codeTotalNfratMd.add(pznfratMd);
                            codeTotalNfratMc=codeTotalNfratMc.add(pznfratMc);

                            String bprogerty="";
                            if(codeinfo.getBprogerty().equals("1")){
                                yue=yue.add(pzmd).subtract(pzmc);
                                yue_num=yue_num.add(pzndS).subtract(pzncS);
                                yue_nfrat.add(yue_nfrat).subtract(pznfratMc);

                                if(yue.compareTo(BigDecimal.ZERO) == 0){
                                    bprogerty="???";
                                }else if(yue.compareTo(BigDecimal.ZERO)>0){
                                    bprogerty="???";
                                }else{
                                    bprogerty="???";
                                }

                            }else{
                                yue=yue.add(pzmc).subtract(pzmd);
                                yue_num=yue_num.add(pzncS).subtract(pzndS);
                                yue_nfrat=yue_nfrat.add(pznfratMc).subtract(pznfratMd);

                                if(yue.compareTo(BigDecimal.ZERO) == 0){
                                    bprogerty="???";
                                }else if(yue.compareTo(BigDecimal.ZERO)>0){
                                    bprogerty="???";
                                }else{
                                    bprogerty="???";
                                }
                            }
                            BigDecimal tempYue=yue.compareTo(BigDecimal.ZERO) < 0 ? yue.multiply(new BigDecimal(-1)) : yue;
                            BigDecimal tempYueNfract=yue_nfrat.compareTo(BigDecimal.ZERO) > 0 ? yue_nfrat : yue_nfrat.multiply(new BigDecimal(-1));

                            if(pzmd.compareTo(BigDecimal.ZERO)!=0 || pzmc.compareTo(BigDecimal.ZERO)!=0) {
                                KeMuMingXiBigDecimalVo mx=new KeMuMingXiBigDecimalVo();
                                mx.setNumber(i+1)
                                        .setCcode(d.getProjectCode())
                                        .setCcodeName(d.getProjectName())
                                        .setDbillDate(collect.get(i).getDbillDate())
                                        .setInoIdASC(Integer.valueOf(collect.get(i).getInoId()))
                                        .setInoId(collect.get(i).getCsign() + "-" + addLeftZero.addZeroForNum(collect.get(i).getInoId(), 4))
                                        .setCdigest(collect.get(i).getCdigest())
                                        .setMd(pzmd)
                                        .setMc(pzmc)
                                        .setBprogerty(bprogerty)
                                        .setNdS(pzndS)
                                        .setNcS(pzncS)
                                        .setCunitPrice(new BigDecimal(collect.get(i).getCunitPrice()))
                                        .setUnitMeasurement(unitMeasurement)
                                        .setMdF(new BigDecimal(collect.get(i).getMdF()))
                                        .setNfrat_md(pznfratMd)
                                        .setNfrat_mc(pznfratMc)
                                        .setForeignCurrency(foreignCurrency)
                                        .setYue(tempYue)
                                        .setYue_num(yue_num)
                                        .setYue_nfrat(tempYueNfract)
                                        .setTemp1(codeinfo.getCcode())
                                        .setTemp2(codeinfo.getCcodeName());

                                mapList.add(mx);
                                lastYue.set(tempYue);
                                lastYue_num.set(yue_num);
                                lastYue_nfrat.set(tempYueNfract);
                            }
                        }
                        if(codeTotalMd.compareTo(BigDecimal.ZERO)!=0 || codeTotalMc.compareTo(BigDecimal.ZERO)!=0) {
                            mapList.add(new KeMuMingXiBigDecimalVo()
                                .setInoIdASC(0)
                                .setCdigest("????????????")
                                .setCcode(d.getProjectCode())
                                .setCcodeName(d.getProjectName())
                                .setMd(codeTotalMd)
                                .setMc(codeTotalMc)
                                .setNdS(codeTotalNds)
                                .setNcS(codeTotalNcs)
                                .setBprogerty(lastYue.get().compareTo(BigDecimal.ZERO) == 0 ? "???" : lastYue.get().compareTo(BigDecimal.ZERO) > 0 ? "???" : "???")
                                .setNfrat_md(codeTotalNfratMd)
                                .setNfrat_mc(codeTotalNfratMc)
                                .setYue(lastYue.get().compareTo(BigDecimal.ZERO) > 0 ? lastYue.get() : lastYue.get().multiply(new BigDecimal(-1)))
                                .setYue_num(lastYue_num.get())
                                .setYue_nfrat(lastYue_nfrat.get())
                                .setTemp1(codeinfo.getCcode())
                                .setTemp2(codeinfo.getCcodeName())
                            );
                        }
                    });

                    // ??????
                    BigDecimal totalMd=mapList.stream().filter(f->f.getTemp1().equals(km)&&f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getMd).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                    BigDecimal totalMc=mapList.stream().filter(f->f.getTemp1().equals(km)&&f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getMc).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                    BigDecimal totalNds=mapList.stream().filter(f->f.getTemp1().equals(km)&&f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getNdS).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                    BigDecimal totalNcs=mapList.stream().filter(f->f.getTemp1().equals(km)&&f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getNcS).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                    BigDecimal totalNfratMd=mapList.stream().filter(f->f.getTemp1().equals(km)&&f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getNfrat_md).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                    BigDecimal totalNfratMc=mapList.stream().filter(f->f.getTemp1().equals(km)&&f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getNfrat_mc).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);

                    BigDecimal totalyuenum=mapList.stream().filter(f->f.getTemp1().equals(km)&&f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getYue_num).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                    BigDecimal totalyuenf=mapList.stream().filter(f->f.getTemp1().equals(km)&&f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getYue_nfrat).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);

                    BigDecimal total_qcyuemd=mapList.stream().filter(f->f.getTemp1().equals(km)&&f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getTempyue).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                    String bprogerty=codeinfo.getBprogerty().equals("1") ? "???" : "???";
                    BigDecimal totalyue=new BigDecimal(0);
                    if(bprogerty.equals("???")){
                        BigDecimal qm=total_qcyuemd;
                        BigDecimal qc=totalMd.subtract(totalMc);
                        BigDecimal temp=qm.add(qc);
                        totalyue=temp.compareTo(BigDecimal.ZERO)<0?temp.multiply(new BigDecimal(-1)):temp;
                    }else{
                        BigDecimal qm=total_qcyuemd;
                        BigDecimal qc=totalMc.subtract(totalMd);
                        BigDecimal temp=qm.add(qc);
                        totalyue=temp.compareTo(BigDecimal.ZERO)<0?temp.multiply(new BigDecimal(-1)):temp;
                    }

                    if(totalMd.compareTo(BigDecimal.ZERO)!=0 || totalMc.compareTo(BigDecimal.ZERO)!=0) {
                        mapList.add(new KeMuMingXiBigDecimalVo()
                            .setInoIdASC(0)
                            .setCdigest("??????")
                            .setBprogerty(bprogerty)
                            .setMd(totalMd)
                            .setMc(totalMc)
                            .setNdS(totalNds)
                            .setNcS(totalNcs)
                            .setYue(totalyue)
                            .setYue_num(totalyuenum)
                            .setYue_nfrat(totalyuenf)
                            .setNfrat_md(totalNfratMd)
                            .setNfrat_mc(totalNfratMc)
                            .setTemp1(codeinfo.getCcode())
                            .setTemp2(codeinfo.getCcodeName())
                        );
                    }
                    Map<String,Object> excelvo=new HashMap<>();
                    excelvo.put("ccode",codeinfo.getCcode());
                    excelvo.put("ccodeName",codeinfo.getCcodeName());
                    excelvo.put("mxlist", mapList);
                    excellist.add(excelvo);
                });
                return Mono.just(excellist);
            })
            .map(a -> R.ok().setResult(a));

    }
    public Mono<R> exportAll2(String strDate, String endDate, String bz, String ishaveRjz, String styleValue,List<String> ccodeList) {
        String year = strDate.substring(0, 4);
        String month = strDate.substring(4, 6);
        AtomicReference<Long> totalAR = new AtomicReference(0);
        // 1?????????????????????????????????
        return projectRepositoryBase.findAllOrderByProCode2().collectList()
                .flatMap(prolist->{
                    List<ProjectVo> finalNewdeptlist = prolist;

                    // 2 ???????????????????????????????????????
                    return accvoucherRepository.findAllByIyearFuzhuAccvoucher(year,year+"01",endDate).collectList()
                            .flatMap(accdeptlist->{
                                List<DeptCodeAccvoucherVo> newaccdeptlist=accdeptlist;
                                // ????????????????????????
                                if(!"??????".equals(bz)){
                                    newaccdeptlist=accdeptlist.stream().filter(accvd->accvd.getForeignCurrency().equals(bz)).collect(Collectors.toList());
                                }
                                // 3-1???????????????????????????   ???????????????1-5??? ???????????????6-8??? ?????????
                                if("????????????".equals(styleValue)){
                                    newaccdeptlist=accdeptlist.stream().filter(ck->Integer.valueOf(ck.getCcode().substring(0,1))<=5).collect(Collectors.toList());
                                }
                                // 3-2???????????????????????????   ???????????????1-5??? ???????????????6-8??? ?????????
                                else if("????????????".equals(styleValue)){
                                    newaccdeptlist=accdeptlist.stream().filter(ck->Integer.valueOf(ck.getCcode().substring(0,1))>=6&&Integer.valueOf(ck.getCcode().substring(0,1))<=8).collect(Collectors.toList());
                                }
                                // 3-3???????????????????????????
                                else if(!"??????".equals(styleValue)){
                                    newaccdeptlist=accdeptlist.stream().filter(ck->ck.getCclass().equals(styleValue)).collect(Collectors.toList());
                                }

                                // ****** ??????????????????????????????????????? *****????????????????????? ??????????????????
                                if (ishaveRjz.equals("0")) {
                                    newaccdeptlist = accdeptlist.stream().filter(vo -> StringUtils.isNotBlank(vo.getIbook()) && vo.getIbook().equals("1")).collect(Collectors.toList());
                                }
                                // ????????????
                                List<DeptCodeAccvoucherVo> finalNewaccdeptlist = newaccdeptlist;
                                return fuZhuBalanceService.findAllSubjectInitialFuZhuBalance(year, "false",year+"21","")
                                        .flatMap(qclist->{
                                            Map qcmap = (Map) qclist.getResult();
                                            return codeKemuRepository.findByIyearOrderByCcode(year).collectList()
                                                    .flatMap(codelist->{
                                                        return Mono.just(
                                                                CollectOfUtils.mapof("qclist",qcmap.get("tablesData"),"accdeptlist",
                                                                        finalNewaccdeptlist,"deptlist", finalNewdeptlist,"codelist",codelist)
                                                        );
                                                    });
                                        });
                            });
                })
                .flatMap(map->{
                    List<SubjectInitialBalanceVo> qclist = (List<SubjectInitialBalanceVo>) map.get("qclist");
                    List<DeptCodeAccvoucherVo> pzlist = (List<DeptCodeAccvoucherVo>) map.get("accdeptlist");
                    List<ProjectVo> deptlist = (List<ProjectVo>) map.get("deptlist");
                    List<CodeKemu> ccodelist= (List<CodeKemu>) map.get("codelist");  // ????????????
                    // ??????list
                    List<KeMuMingXiBigDecimalVo> mapList = new ArrayList<>();
                    ccodeList.stream().forEach(km->{
                        CodeKemu codeinfo = ccodelist.stream().filter(v -> v.getCcode().equals(km)).collect(Collectors.toList()).get(0);

                        AtomicReference<BigDecimal> lastYue= new AtomicReference<>(new BigDecimal(0));
                        AtomicReference<BigDecimal> lastYue_num= new AtomicReference<>(new BigDecimal(0));
                        AtomicReference<BigDecimal> lastYue_nfrat= new AtomicReference<>(new BigDecimal(0));

                        List<ProjectVo> proVolist = deptlist.stream().filter(v -> v.getCcode().equals(km)).collect(Collectors.toList());
                        proVolist.stream().forEach(d->{
                            BigDecimal codeTotalMd=new BigDecimal(0);
                            BigDecimal codeTotalMc=new BigDecimal(0);
                            BigDecimal codeTotalNds=new BigDecimal(0);
                            BigDecimal codeTotalNcs=new BigDecimal(0);
                            BigDecimal codeTotalNfratMd=new BigDecimal(0);
                            BigDecimal codeTotalNfratMc=new BigDecimal(0);
                            // ?????????????????????
                            String unitMeasurement=codeinfo.getMenterage();
                            // ?????????????????????
                            String foreignCurrency =codeinfo.getCurrencyType();
                            // ************* ?????????????????? *************
                            List<SubjectInitialBalanceVo> findByCodeQcList = qclist.stream().filter(qc ->StrUtil.isNotBlank(qc.getProjectId()) && qc.getCcode().equals(km) && qc.getProjectId().equals(d.getUniqueCode()) ).collect(Collectors.toList());
                            BigDecimal qcmd=findByCodeQcList.size()>0?findByCodeQcList.get(0).getMd():new BigDecimal(0);
                            BigDecimal qcmc=findByCodeQcList.size()>0?findByCodeQcList.get(0).getMc():new BigDecimal(0);
                            BigDecimal qcndS=findByCodeQcList.size()>0?findByCodeQcList.get(0).getNdS():new BigDecimal(0);
                            BigDecimal qcncS=findByCodeQcList.size()>0?findByCodeQcList.get(0).getNcS():new BigDecimal(0);
                            BigDecimal qcnfratMd=findByCodeQcList.size()>0?findByCodeQcList.get(0).getNfratMd():new BigDecimal(0);
                            BigDecimal qcnfratMc=findByCodeQcList.size()>0?findByCodeQcList.get(0).getNfratMc():new BigDecimal(0);
                            BigDecimal yue=new BigDecimal(0);
                            BigDecimal yue_num=new BigDecimal(0);
                            BigDecimal yue_nfrat=new BigDecimal(0);

                            BigDecimal lastmd=new BigDecimal(0);
                            BigDecimal lastmc=new BigDecimal(0);
                            BigDecimal lastndS=new BigDecimal(0);
                            BigDecimal lastncS=new BigDecimal(0);
                            BigDecimal lastnfratMd=new BigDecimal(0);
                            BigDecimal lastnfratMc=new BigDecimal(0);

                            // ??????????????? ???????????????????????? ??????????????????????????????
                            if (Integer.valueOf(strDate.substring(4, 6)) > 1) {
                                String lastmonth = Integer.valueOf(month) - 1 < 10 ? "0" + (Integer.valueOf(month) - 1) : month;
                                // ??????????????????????????????
                                List<DeptCodeAccvoucherVo> lastMonthAccVoucher = pzlist.stream().filter(acv -> Integer.valueOf(acv.getImonth()) <= Integer.valueOf(lastmonth) && acv.getCcode().equals(codeinfo.getCcode()) && acv.getProjectId().equals(d.getUniqueCode())).collect(Collectors.toList());
                                lastmd=lastMonthAccVoucher.size()>0?lastMonthAccVoucher.stream().map(DeptCodeAccvoucherVo::getMd).reduce(BigDecimal.ZERO, BigDecimalUtils::sum):new BigDecimal(0);
                                lastmc=lastMonthAccVoucher.size()>0?lastMonthAccVoucher.stream().map(DeptCodeAccvoucherVo::getMc).reduce(BigDecimal.ZERO, BigDecimalUtils::sum):new BigDecimal(0);
                                lastndS=lastMonthAccVoucher.size()>0?lastMonthAccVoucher.stream().map(DeptCodeAccvoucherVo::getNdS).reduce(BigDecimal.ZERO, BigDecimalUtils::sum):new BigDecimal(0);
                                lastncS=lastMonthAccVoucher.size()>0?lastMonthAccVoucher.stream().map(DeptCodeAccvoucherVo::getNcS).reduce(BigDecimal.ZERO, BigDecimalUtils::sum):new BigDecimal(0);
                                lastnfratMd=lastMonthAccVoucher.size()>0?lastMonthAccVoucher.stream().map(DeptCodeAccvoucherVo::getNfratMd).reduce(BigDecimal.ZERO, BigDecimalUtils::sum):new BigDecimal(0);
                                lastnfratMc=lastMonthAccVoucher.size()>0?lastMonthAccVoucher.stream().map(DeptCodeAccvoucherVo::getNfratMc).reduce(BigDecimal.ZERO, BigDecimalUtils::sum):new BigDecimal(0);
                            }

                            String qc_bprogerty="";
                            // ??????
                            if(codeinfo.getBprogerty().equals("1")){
                                yue=qcmd.add(lastmd).subtract(qcmc.add(lastmc));
                                yue_num=qcndS.add(lastndS).subtract(qcncS.add(lastncS));
                                yue_nfrat=qcnfratMd.add(lastnfratMd).subtract(qcnfratMc.add(lastnfratMc));
                                if(yue.compareTo(BigDecimal.ZERO) == 0){
                                    qc_bprogerty="???";
                                }else if(yue.compareTo(BigDecimal.ZERO)>0){
                                    qc_bprogerty="???";
                                }else{
                                    qc_bprogerty="???";
                                }
                            }else{
                                yue=qcmc.add(lastmc).subtract(qcmd.add(lastmd));
                                yue_num=qcncS.add(lastncS).subtract(qcndS.add(lastndS));
                                yue_nfrat=qcnfratMc.add(lastnfratMc).subtract(qcnfratMd.add(lastnfratMd));

                                if(yue.compareTo(BigDecimal.ZERO) == 0){
                                    qc_bprogerty="???";
                                }else if(yue.compareTo(BigDecimal.ZERO)>0){
                                    qc_bprogerty="???";
                                }else{
                                    qc_bprogerty="???";
                                }
                            }
                            if(yue.compareTo(BigDecimal.ZERO)!=0){
                                mapList.add(
                                        new KeMuMingXiBigDecimalVo()
                                                .setInoIdASC(0)
                                                .setCcode(d.getProjectCode())
                                                .setCcodeName(d.getProjectName())
                                                .setCdigest("????????????")
                                                .setBprogerty(qc_bprogerty)
                                                .setYue(yue.compareTo(BigDecimal.ZERO) < 0?yue.multiply(new BigDecimal(-1)):yue)
                                                .setTempyue(yue)
                                                .setNcnum(yue_num.compareTo(BigDecimal.ZERO) < 0?yue_num.multiply(new BigDecimal(-1)):yue_num)
                                                .setYue_num(yue_num.compareTo(BigDecimal.ZERO) < 0?yue_num.multiply(new BigDecimal(-1)):yue_num)
                                                .setYue_nfrat(yue_nfrat.compareTo(BigDecimal.ZERO) < 0?yue_nfrat.multiply(new BigDecimal(-1)):yue_nfrat)
                                                .setTemp1(codeinfo.getCcode())
                                                .setTemp2(codeinfo.getCcodeName())
                                );
                            }

                            // ************* ???????????????????????? *************
                            List<DeptCodeAccvoucherVo> collect = pzlist.stream().filter(pz ->StrUtil.isNotBlank(pz.getProjectId())&& pz.getProjectId().equals(d.getUniqueCode())&&pz.getCcode().equals(km) && Integer.valueOf(pz.getIyperiod())>=Integer.valueOf(strDate) && Integer.valueOf(pz.getIyperiod())<=Integer.valueOf(endDate)).collect(Collectors.toList());
                            for (int i = 0; i < collect.size(); i++) {
                                BigDecimal pzmd=collect.size()>0?collect.get(i).getMd():new BigDecimal(0);
                                BigDecimal pzmc=collect.size()>0?collect.get(i).getMc():new BigDecimal(0);
                                BigDecimal pzndS=collect.size()>0?collect.get(i).getNdS():new BigDecimal(0);
                                BigDecimal pzncS=collect.size()>0?collect.get(i).getNcS():new BigDecimal(0);
                                BigDecimal pznfratMd=collect.size()>0?collect.get(i).getNfratMd():new BigDecimal(0);
                                BigDecimal pznfratMc=collect.size()>0?collect.get(i).getNfratMc():new BigDecimal(0);

                                // ??????
                                codeTotalMd=codeTotalMd.add(pzmd);
                                codeTotalMc=codeTotalMc.add(pzmc);
                                codeTotalNds=codeTotalNds.add(pzndS);
                                codeTotalNcs=codeTotalNcs.add(pzncS);
                                codeTotalNfratMd=codeTotalNfratMd.add(pznfratMd);
                                codeTotalNfratMc=codeTotalNfratMc.add(pznfratMc);

                                String bprogerty="";
                                if(codeinfo.getBprogerty().equals("1")){
                                    yue=yue.add(pzmd).subtract(pzmc);
                                    yue_num=yue_num.add(pzndS).subtract(pzncS);
                                    yue_nfrat.add(yue_nfrat).subtract(pznfratMc);

                                    if(yue.compareTo(BigDecimal.ZERO) == 0){
                                        bprogerty="???";
                                    }else if(yue.compareTo(BigDecimal.ZERO)>0){
                                        bprogerty="???";
                                    }else{
                                        bprogerty="???";
                                    }

                                }else{
                                    yue=yue.add(pzmc).subtract(pzmd);
                                    yue_num=yue_num.add(pzncS).subtract(pzndS);
                                    yue_nfrat=yue_nfrat.add(pznfratMc).subtract(pznfratMd);

                                    if(yue.compareTo(BigDecimal.ZERO) == 0){
                                        bprogerty="???";
                                    }else if(yue.compareTo(BigDecimal.ZERO)>0){
                                        bprogerty="???";
                                    }else{
                                        bprogerty="???";
                                    }
                                }
                                BigDecimal tempYue=yue.compareTo(BigDecimal.ZERO) < 0 ? yue.multiply(new BigDecimal(-1)) : yue;
                                BigDecimal tempYueNfract=yue_nfrat.compareTo(BigDecimal.ZERO) > 0 ? yue_nfrat : yue_nfrat.multiply(new BigDecimal(-1));

                                if(pzmd.compareTo(BigDecimal.ZERO)!=0 || pzmc.compareTo(BigDecimal.ZERO)!=0) {
                                    KeMuMingXiBigDecimalVo mx=new KeMuMingXiBigDecimalVo();
                                    mx.setNumber(i+1)
                                            .setCcode(d.getProjectCode())
                                            .setCcodeName(d.getProjectName())
                                            .setDbillDate(collect.get(i).getDbillDate())
                                            .setInoIdASC(Integer.valueOf(collect.get(i).getInoId()))
                                            .setInoId(collect.get(i).getCsign() + "-" + addLeftZero.addZeroForNum(collect.get(i).getInoId(), 4))
                                            .setCdigest(collect.get(i).getCdigest())
                                            .setMd(pzmd)
                                            .setMc(pzmc)
                                            .setBprogerty(bprogerty)
                                            .setNdS(pzndS)
                                            .setNcS(pzncS)
                                            .setCunitPrice(new BigDecimal(collect.get(i).getCunitPrice()))
                                            .setUnitMeasurement(unitMeasurement)
                                            .setMdF(new BigDecimal(collect.get(i).getMdF()))
                                            .setNfrat_md(pznfratMd)
                                            .setNfrat_mc(pznfratMc)
                                            .setForeignCurrency(foreignCurrency)
                                            .setYue(tempYue)
                                            .setYue_num(yue_num)
                                            .setYue_nfrat(tempYueNfract)
                                            .setTemp1(codeinfo.getCcode())
                                            .setTemp2(codeinfo.getCcodeName());

                                    mapList.add(mx);
                                    lastYue.set(tempYue);
                                    lastYue_num.set(yue_num);
                                    lastYue_nfrat.set(tempYueNfract);
                                }
                            }
                            if(codeTotalMd.compareTo(BigDecimal.ZERO)!=0 || codeTotalMc.compareTo(BigDecimal.ZERO)!=0) {
                                mapList.add(new KeMuMingXiBigDecimalVo()
                                        .setInoIdASC(0)
                                        .setCdigest("????????????")
                                        .setCcode(d.getProjectCode())
                                        .setCcodeName(d.getProjectName())
                                        .setMd(codeTotalMd)
                                        .setMc(codeTotalMc)
                                        .setNdS(codeTotalNds)
                                        .setNcS(codeTotalNcs)
                                        .setBprogerty(lastYue.get().compareTo(BigDecimal.ZERO) == 0 ? "???" : lastYue.get().compareTo(BigDecimal.ZERO) > 0 ? "???" : "???")
                                        .setNfrat_md(codeTotalNfratMd)
                                        .setNfrat_mc(codeTotalNfratMc)
                                        .setYue(lastYue.get().compareTo(BigDecimal.ZERO) > 0 ? lastYue.get() : lastYue.get().multiply(new BigDecimal(-1)))
                                        .setYue_num(lastYue_num.get())
                                        .setYue_nfrat(lastYue_nfrat.get())
                                        .setTemp1(codeinfo.getCcode())
                                        .setTemp2(codeinfo.getCcodeName())
                                );
                            }
                        });

                        // ??????
                        BigDecimal totalMd=mapList.stream().filter(f->f.getTemp1().equals(km)&&f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getMd).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                        BigDecimal totalMc=mapList.stream().filter(f->f.getTemp1().equals(km)&&f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getMc).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                        BigDecimal totalNds=mapList.stream().filter(f->f.getTemp1().equals(km)&&f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getNdS).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                        BigDecimal totalNcs=mapList.stream().filter(f->f.getTemp1().equals(km)&&f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getNcS).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                        BigDecimal totalNfratMd=mapList.stream().filter(f->f.getTemp1().equals(km)&&f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getNfrat_md).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                        BigDecimal totalNfratMc=mapList.stream().filter(f->f.getTemp1().equals(km)&&f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getNfrat_mc).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);

                        BigDecimal totalyuenum=mapList.stream().filter(f->f.getTemp1().equals(km)&&f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getYue_num).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                        BigDecimal totalyuenf=mapList.stream().filter(f->f.getTemp1().equals(km)&&f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getYue_nfrat).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);

                        BigDecimal total_qcyuemd=mapList.stream().filter(f->f.getTemp1().equals(km)&&f.getCdigest().equals("????????????")).map(KeMuMingXiBigDecimalVo::getTempyue).reduce(BigDecimal.ZERO, BigDecimalUtils::sum);
                        String bprogerty=codeinfo.getBprogerty().equals("1") ? "???" : "???";
                        BigDecimal totalyue=new BigDecimal(0);
                        if(bprogerty.equals("???")){
                            BigDecimal qm=total_qcyuemd;
                            BigDecimal qc=totalMd.subtract(totalMc);
                            BigDecimal temp=qm.add(qc);
                            totalyue=temp.compareTo(BigDecimal.ZERO)<0?temp.multiply(new BigDecimal(-1)):temp;
                        }else{
                            BigDecimal qm=total_qcyuemd;
                            BigDecimal qc=totalMc.subtract(totalMd);
                            BigDecimal temp=qm.add(qc);
                            totalyue=temp.compareTo(BigDecimal.ZERO)<0?temp.multiply(new BigDecimal(-1)):temp;
                        }

                        if(totalMd.compareTo(BigDecimal.ZERO)!=0 || totalMc.compareTo(BigDecimal.ZERO)!=0) {
                            mapList.add(new KeMuMingXiBigDecimalVo()
                                    .setInoIdASC(0)
                                    .setCdigest("??????")
                                    .setBprogerty(bprogerty)
                                    .setMd(totalMd)
                                    .setMc(totalMc)
                                    .setNdS(totalNds)
                                    .setNcS(totalNcs)
                                    .setYue(totalyue)
                                    .setYue_num(totalyuenum)
                                    .setYue_nfrat(totalyuenf)
                                    .setNfrat_md(totalNfratMd)
                                    .setNfrat_mc(totalNfratMc)
                                    .setTemp1(codeinfo.getCcode())
                                    .setTemp2(codeinfo.getCcodeName())
                            );
                        }
                    });
                    return Mono.just(mapList);
                })
                .map(a -> R.ok().setResult(a));

    }
}
