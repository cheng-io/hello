package org.boozsoft.rest.stock;

import cn.hutool.core.util.StrUtil;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.boozsoft.domain.entity.FileEncodingRules;
import org.boozsoft.domain.entity.SysUnitOfMea;
import org.boozsoft.domain.entity.SysUnitOfMeaList;
import org.boozsoft.domain.entity.Task;
import org.boozsoft.domain.entity.stock.Stock;
import org.boozsoft.domain.entity.stock.StockBeginBalance;
import org.boozsoft.domain.entity.stock.StockClass;
import org.boozsoft.domain.ro.GeneralLedgerRo;
import org.boozsoft.domain.vo.stock.StockAccSheetVo;
import org.boozsoft.domain.vo.stock.StockKctzRestVo;
import org.boozsoft.domain.vo.stock.StockKctzVo;
import org.boozsoft.domain.vo.stock.StockVo;
import org.boozsoft.repo.*;
import org.boozsoft.repo.stock.*;
import org.boozsoft.util.XlsUtils3;
import org.springbooz.core.tool.result.R;
import org.springbooz.core.tool.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/stock/accountSheet")
public class StockAccountSheetController {

    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private StockWarehousingsRepository warehousingsRepository;
    @Autowired
    private StockSaleousingsRepository saleousingsRepository;
    @Autowired
    private StockBeginBalanceRepository stockBeginBalanceRepository;
    @Autowired
    private StockCurrentstockRepository stockCurrentstockRepository;
    @Autowired
    private TaskRepository taskRepository;

    //????????????
    @PostMapping(value = "/findAll")
    @ApiOperation(value = "??????????????????", notes = "??????????????????")
    public Mono<R> findAll(@RequestBody Map maps) {
        String strDate = maps.get("dateStart").toString().replace(".", "-");
        String endDate = maps.get("dateEnd").toString().replace(".", "-");
        String ch = maps.get("ch").toString();
        String ck = maps.containsKey("ck")?maps.get("ck").toString():"";
        String flg = maps.containsKey("flg")?maps.get("flg").toString():"";
        //?????? ??????  
        List<StockKctzVo> skl = new ArrayList<>();
        StockKctzRestVo sk = new StockKctzRestVo();
        StockKctzVo sv = new StockKctzVo();
        sv.setBillStyle("????????????");
        sv.setCmakerTime("");
        sv.setDdate("");
        sv.setCcode("");
        sv.setBcheck("6");
        return Mono.just(sv)
                .flatMap(obj->{
                    return  stockBeginBalanceRepository.findAllByStockId(ch)
                            .collectList()
                            .map(wl->{
                                //??????
                                List<StockBeginBalance>  skvList = wl.stream()
                                        .filter(v->{
                                            if(Objects.nonNull(ck) && ck.length()>0){
                                                return v.getStockCangkuId().equals(ck);
                                            }
                                            return true;
                                        }).filter(v->{
                                            if(Objects.nonNull(flg)&& flg.length()>0){
                                                v.setBcheck(Objects.nonNull(v.getBcheck()) ? v.getBcheck() : "0");
                                                if(v.getBcheck().equals("6")){
                                                    return true;
                                                }
                                                if(flg.equals("5")){
                                                    return true;
                                                }else{
                                                    return v.getBcheck().equals(flg);
                                                }
                                            }
                                            return true;
                                        }).collect(Collectors.toList());

                                //????????????????????????
                                double sumBq = skvList.stream().mapToDouble(v -> {
                                    if (Objects.isNull(v.getBaseQuantity())) return 0.00d;
                                    return Double.parseDouble(v.getBaseQuantity().toString());
                                }).sum();
                                double sumIt = skvList.stream().mapToDouble(v -> {
                                    if (Objects.isNull(v.getIcost())) return 0.00d;
                                    return Double.parseDouble(v.getIcost().toString());
                                }).sum();

                                obj.setBq2(sumBq+"");
                                obj.setIcost2(sumIt+"");
                                return obj;
                            });
                })
                .flatMap(obj->{
                    //??????
                    String str = strDate.split("-")[0] + "-01-01";
                    return  warehousingsRepository.findAllByCinvodeAndDate(ch,str,strDate)
                            .collectList()
                            .map(wl->{
                                //??????
                                List<StockKctzVo>  skvList = wl.stream()
                                        .filter(v->{
                                            if(Objects.nonNull(ck) && ck.length()>0){
                                                return v.getCwhcode().equals(ck);
                                            }
                                            return true;
                                        }).filter(v->{
                                            if(Objects.nonNull(flg)&& flg.length()>0){
                                                v.setBcheck(Objects.nonNull(v.getBcheck()) ? v.getBcheck() : "0");
                                                if(v.getBcheck().equals("6")){
                                                    return true;
                                                }
                                                if(flg.equals("5")){
                                                    return true;
                                                }else{
                                                    return v.getBcheck().equals(flg);
                                                }
                                            }
                                            return true;
                                        }).collect(Collectors.toList());
                                //????????????????????????
                                double sumBq = skvList.stream().mapToDouble(v -> {
                                    if (Objects.isNull(v.getBq())) return 0.00d;
                                    return Double.parseDouble(v.getBq());
                                }).sum();
                                double sumIt = skvList.stream().mapToDouble(v -> {
                                    if (Objects.isNull(v.getIcost())) return 0.00d;
                                    return Double.parseDouble(v.getIcost());
                                }).sum();

                                obj.setBq(sumBq+"");
                                obj.setIcost(sumIt+"");
                                return obj;
                            });
                })
                .flatMap(obj->{
                    //??????
                    String str = strDate.split("-")[0] + "-01-01";
                    return saleousingsRepository.findAllByCinvodeAndDate(ch,strDate,strDate)
                            .collectList()
                            .map(sl->{
                                //??????
                                List<StockKctzVo>  skvList = sl.stream()
                                        .filter(v->{
                                            if(Objects.nonNull(ck) && ck.length()>0){
                                                return v.getCwhcode().equals(ck);
                                            }
                                            return true;
                                        }).filter(v->{
                                            if(Objects.nonNull(flg)&& flg.length()>0){
                                                v.setBcheck(Objects.nonNull(v.getBcheck()) ? v.getBcheck() : "0");
                                                if(v.getBcheck().equals("6")){
                                                    return true;
                                                }
                                                if(flg.equals("5")){
                                                    return true;
                                                }else{
                                                    return v.getBcheck().equals(flg);
                                                }
                                            }
                                            return true;
                                        }).collect(Collectors.toList());
                                //????????????????????????
                                double sumBq1 = skvList.stream().mapToDouble(v -> {
                                    if (Objects.isNull(v.getBq1())) return 0.00d;
                                    return Double.parseDouble(v.getBq1());
                                }).sum();
                                double sumIt1 = skvList.stream().mapToDouble(v -> {
                                    if (Objects.isNull(v.getIcost1())) return 0.00d;
                                    return Double.parseDouble(v.getIcost1());
                                }).sum();
                                obj.setBq1(sumBq1+"");
                                obj.setIcost1(sumIt1+"");
                                return obj;
                            });
                })
                .map(obj->{
                    //??????-?????? = ??????
                    BigDecimal bq = new BigDecimal(obj.getBq()).subtract(new BigDecimal(obj.getBq1()));
                    BigDecimal bq1 =new BigDecimal(obj.getIcost()).subtract(new BigDecimal(obj.getIcost1()));
                    obj.setBq2(bq.add(new BigDecimal(obj.getBq2())).toString());
                    obj.setIcost2(bq1.add(new BigDecimal(obj.getIcost2())).toString());
                    if(Objects.nonNull(obj.getBq2()) && new BigDecimal(obj.getBq2()).compareTo(BigDecimal.ZERO) !=0){
                        obj.setPrice2(new BigDecimal(obj.getIcost2()).divide(new BigDecimal(obj.getBq2()),2,BigDecimal.ROUND_HALF_UP).toString());
                    }
                    skl.add(obj);
                    return skl;
                })
                .flatMap(list->{
                    return  warehousingsRepository.findAllByCinvodeAndDate(ch,strDate,endDate)
                            .collectList()
                            .map(wl->{
                                if(wl.size() > 0) list.addAll(wl);
                                return list;
                            });
                })
                .flatMap(list->{
                    return saleousingsRepository.findAllByCinvodeAndDate(ch,strDate,endDate)
                            .collectList()
                            .map(sl->{
                                if(sl.size() > 0) list.addAll(sl);
                                return  list;
                            });
                })
                .map(list->{
                    //??????
                   return list.stream()
                           .filter(v->{
                               if(Objects.nonNull(ck) && ck.length()>0){
                                   if("6".equals(v.getBcheck())){
                                       return true;
                                   }
                                   return v.getCwhcode().equals(ck);
                               }
                               return true;
                           }).filter(v->{
                               if(Objects.nonNull(flg)&& flg.length()>0){
                                   v.setBcheck(Objects.nonNull(v.getBcheck()) ? v.getBcheck() : "0");
                                   if(v.getBcheck().equals("6")){
                                       return true;
                                   }
                                   if(flg.equals("5")){
                                       return true;
                                   }else{
                                       return v.getBcheck().equals(flg);
                                   }
                               }
                               return true;
                           }).collect(Collectors.toList());
                })
                .map(list->{
                    //?????????list?????????
                    List<StockKctzVo> resList = list.stream().sorted(
                                    Comparator.comparing(StockKctzVo::getDdate)
                                            .thenComparing(StockKctzVo::getCmakerTime))//??????????????????????????????(status_rank??????)????????????
                            .collect(Collectors.toList());
                    //???????????? 
                    //??????
                    AtomicReference<Integer> index = new AtomicReference<>(0);
                    resList.forEach(v->{
                        if(Objects.isNull(v.getComcode())){
                            if(Objects.isNull(v.getComcode2())){
                                v.setComcode(v.getComcode3());
                            }else{
                                v.setComcode(v.getComcode2());
                            }
                        }
                        //??????
                        if(Objects.nonNull(v.getBq()) && index.get() != 0){

                            if(!v.getBillStyle().equals("RKTZD")&&!v.getBillStyle().equals("CKTZD")){
                                v.setPrice(Objects.nonNull(v.getIcost()) ? new BigDecimal(v.getIcost()).divide(new BigDecimal(v.getBq()),BigDecimal.ROUND_HALF_UP).toString() : null);
                                v.setPrice1(Objects.nonNull(v.getIcost1()) ? new BigDecimal(v.getIcost1()).divide(new BigDecimal(v.getBq1()),BigDecimal.ROUND_HALF_UP).toString(): null);

                                StockKctzVo stockKctzVo = resList.get(index.get()-1);
                                String bq2 = new BigDecimal(v.getBq()).add(new BigDecimal(stockKctzVo.getBq2())).setScale(2,BigDecimal.ROUND_HALF_UP).toString();
                                String it2 = new BigDecimal(Objects.isNull(v.getIcost())?"0.00":v.getIcost()).add(new BigDecimal(stockKctzVo.getIcost2())).setScale(2,BigDecimal.ROUND_UP).toString();
                                v.setBq2(bq2);
                                v.setIcost2(it2);
                                if("0.00".equals(it2)){
                                    v.setPrice2("0.00");
                                }else{
                                    v.setPrice2(new BigDecimal(it2).divide(new BigDecimal(bq2), 2, BigDecimal.ROUND_HALF_UP).toString());
                                }
                            }else{
                                StockKctzVo stockKctzVo = resList.get(index.get()-1);
                                String it2 = new BigDecimal(Objects.isNull(v.getIcost())?"0.00":v.getIcost()).add(new BigDecimal(stockKctzVo.getIcost2())).setScale(2,BigDecimal.ROUND_UP).toString();
                                v.setIcost2(it2);
                                v.setBq2("0");
                            }
                        }
                        //??????
                        if(Objects.nonNull(v.getBq1()) && index.get() != 0){
                            if(!v.getBillStyle().equals("RKTZD")&&!v.getBillStyle().equals("CKTZD")){
                                v.setPrice(Objects.nonNull(v.getIcost()) ? new BigDecimal(v.getIcost()).divide(new BigDecimal(v.getBq()),BigDecimal.ROUND_HALF_UP).toString() : null);
                                v.setPrice1(Objects.nonNull(v.getIcost1()) ? new BigDecimal(v.getIcost1()).divide(new BigDecimal(v.getBq1()),BigDecimal.ROUND_HALF_UP).toString(): null);

                                StockKctzVo stockKctzVo = resList.get(index.get()-1);
                                String bq2 = new BigDecimal(stockKctzVo.getBq2()).subtract(new BigDecimal(v.getBq1())).setScale(2,BigDecimal.ROUND_HALF_UP).toString();
                                String it2 = new BigDecimal(stockKctzVo.getIcost2()).subtract( new BigDecimal(Objects.isNull(v.getIcost1())?"0.00":v.getIcost1())).setScale(2,BigDecimal.ROUND_UP).toString();
                                v.setBq2(bq2);
                                v.setIcost2(it2);
                                if("0.00".equals(it2) || "0.00".equals(bq2)){
                                    v.setPrice2("0.00");
                                }else{
                                    v.setPrice2(new BigDecimal(it2).divide(new BigDecimal(bq2), 2, BigDecimal.ROUND_HALF_UP).toString());
                                }
                            }else{
                                StockKctzVo stockKctzVo = resList.get(index.get()-1);
                                String it2 = new BigDecimal(Objects.isNull(v.getIcost())?"0.00":v.getIcost()).add(new BigDecimal(stockKctzVo.getIcost2())).setScale(2,BigDecimal.ROUND_UP).toString();
                                v.setIcost2(it2);
                                v.setBq2("0");
                            }
                        }
                        //??????????????????  ?????? ??????
                        index.getAndSet(index.get() + 1);
                    });

                    sk.setList(resList);
                    sk.setSize(resList.size());
                    return resList;
                })
                .map(list-> R.ok().setResult(sk));
    }

    //????????????
    @PostMapping(value = "/pcfindAll")
    @ApiOperation(value = "??????????????????", notes = "??????????????????")
    public Mono<R> pcfindAll(@RequestBody Map maps) {
        String strDate = maps.get("dateStart").toString().replace(".", "-");
        String endDate = maps.get("dateEnd").toString().replace(".", "-");
        String ch = maps.get("ch").toString();
        String ck = maps.containsKey("ck")?maps.get("ck").toString():"";
        String pc = maps.containsKey("pcNum")?maps.get("pcNum").toString():"";
        String flg = maps.containsKey("flg")?maps.get("flg").toString():"";
        //?????? ??????
        List<StockKctzVo> skl = new ArrayList<>();
        StockKctzRestVo sk = new StockKctzRestVo();
        StockKctzVo sv = new StockKctzVo();
        sv.setBillStyle("????????????");
        sv.setDdate("");
        sv.setCmakerTime("");
        sv.setCcode("");
        sv.setBcheck("6");
        return Mono.just(sv)
                .flatMap(obj->{
                    return  stockBeginBalanceRepository.findAllByStockIdAndBatchNumber(ch,pc)
                            .collectList()
                            .map(wl->{
                                //??????
                                List<StockBeginBalance>  skvList = wl.stream()
                                        .filter(v->{
                                            if(Objects.nonNull(ck) && ck.length()>0){
                                                return v.getStockCangkuId().equals(ck);
                                            }
                                            return true;
                                        }).filter(v->{
                                            if(Objects.nonNull(flg)&& flg.length()>0){
                                                v.setBcheck(Objects.nonNull(v.getBcheck()) ? v.getBcheck() : "0");
                                                if(v.getBcheck().equals("6")){
                                                    return true;
                                                }
                                                if(flg.equals("5")){
                                                    return true;
                                                }else{
                                                    return v.getBcheck().equals(flg);
                                                }
                                            }
                                            return true;
                                        }).collect(Collectors.toList());

                                //????????????????????????
                                double sumBq = skvList.stream().mapToDouble(v -> {
                                    if (Objects.isNull(v.getBaseQuantity())) return 0.00d;
                                    return Double.parseDouble(v.getBaseQuantity().toString());
                                }).sum();
                                double sumIt = skvList.stream().mapToDouble(v -> {
                                    if (Objects.isNull(v.getIcost())) return 0.00d;
                                    return Double.parseDouble(v.getIcost().toString());
                                }).sum();

                                obj.setBq2(sumBq+"");
                                obj.setIcost2(sumIt+"");
                                return obj;
                            });
                })
                .flatMap(obj->{
                    //??????
                    String str = strDate.split("-")[0] + "-01-01";
                    return  warehousingsRepository.findAllByCinvodeAndDateAndPc(ch,pc,str,strDate)
                            .collectList()
                            .map(wl->{
                                //??????
                                List<StockKctzVo>  skvList = wl.stream()
                                        .filter(v->{
                                            if(Objects.nonNull(ck) && ck.length()>0){
                                                return v.getCwhcode().equals(ck);
                                            }
                                            return true;
                                        }).filter(v->{
                                            if(Objects.nonNull(flg)&& flg.length()>0){
                                                v.setBcheck(Objects.nonNull(v.getBcheck()) ? v.getBcheck() : "0");
                                                if(v.getBcheck().equals("6")){
                                                    return true;
                                                }
                                                if(flg.equals("5")){
                                                    return true;
                                                }else{
                                                    return v.getBcheck().equals(flg);
                                                }
                                            }
                                            return true;
                                        }).collect(Collectors.toList());
                                //????????????????????????
                                double sumBq = skvList.stream().mapToDouble(v -> {
                                    if (Objects.isNull(v.getBq())) return 0.00d;
                                    return Double.parseDouble(v.getBq());
                                }).sum();
                                double sumIt = skvList.stream().mapToDouble(v -> {
                                    if (Objects.isNull(v.getIcost())) return 0.00d;
                                    return Double.parseDouble(v.getIcost());
                                }).sum();

                                obj.setBq(sumBq+"");
                                obj.setIcost(sumIt+"");
                                return obj;
                            });
                })
                .flatMap(obj->{
                    //??????
                    String str = strDate.split("-")[0] + "-01-01";
                    return saleousingsRepository.findAllByCinvodeAndDateAndPc(ch,pc,strDate,strDate)
                            .collectList()
                            .map(sl->{
                                //??????
                                List<StockKctzVo>  skvList = sl.stream()
                                        .filter(v->{
                                            if(Objects.nonNull(ck) && ck.length()>0){
                                                return v.getCwhcode().equals(ck);
                                            }
                                            return true;
                                        }).filter(v->{
                                            if(Objects.nonNull(flg)&& flg.length()>0){
                                                v.setBcheck(Objects.nonNull(v.getBcheck()) ? v.getBcheck() : "0");
                                                if("6".equals(v.getBcheck())){
                                                    return true;
                                                }
                                                if(flg.equals("5")){
                                                    return true;
                                                }else{
                                                    return v.getBcheck().equals(flg);
                                                }
                                            }
                                            return true;
                                        }).collect(Collectors.toList());
                                //????????????????????????
                                double sumBq1 = skvList.stream().mapToDouble(v -> {
                                    if (Objects.isNull(v.getBq1())) return 0.00d;
                                    return Double.parseDouble(v.getBq1());
                                }).sum();
                                double sumIt1 = skvList.stream().mapToDouble(v -> {
                                    if (Objects.isNull(v.getIcost1())) return 0.00d;
                                    return Double.parseDouble(v.getIcost1());
                                }).sum();
                                obj.setBq1(sumBq1+"");
                                obj.setIcost1(sumIt1+"");
                                return obj;
                            });
                })
                .map(obj->{
                    //??????-?????? = ??????
                    BigDecimal bq = new BigDecimal(obj.getBq()).subtract(new BigDecimal(obj.getBq1())).setScale(2, BigDecimal.ROUND_HALF_UP);
                    BigDecimal bq1 =new BigDecimal(obj.getIcost()).subtract(new BigDecimal(obj.getIcost1())).setScale(2,BigDecimal.ROUND_HALF_UP);
                    obj.setBq2(bq.add(new BigDecimal(obj.getBq2())).toString());
                    obj.setIcost2(bq1.add(new BigDecimal(obj.getIcost2())).toString());
                    if(Objects.nonNull(obj.getBq2()) && !"0.00".equals(obj.getBq2())){
                        obj.setPrice2(new BigDecimal(obj.getIcost2()).divide(new BigDecimal(obj.getBq2()),2,BigDecimal.ROUND_HALF_UP).toString());
                    }
                    skl.add(obj);
                    return skl;
                })
                .flatMap(list->{
                    return  warehousingsRepository.findAllByCinvodeAndDateAndPc(ch,pc,strDate,endDate)
                            .collectList()
                            .map(wl->{
                                if(wl.size() > 0) list.addAll(wl);
                                return list;
                            });
                })
                .flatMap(list->{
                    return saleousingsRepository.findAllByCinvodeAndDateAndPc(ch,pc,strDate,endDate)
                            .collectList()
                            .map(sl->{
                                if(sl.size() > 0) list.addAll(sl);
                                return  list;
                            });
                })
                .map(list->{
                    //??????
                    return list.stream()
                            .filter(v->{
                                if(Objects.nonNull(ck) && ck.length()>0){
                                    if("6".equals(v.getBcheck())){
                                        return true;
                                    }
                                    return v.getCwhcode().equals(ck);
                                }
                                return true;
                            }).filter(v->{
                                if(Objects.nonNull(flg)&& flg.length()>0){
                                    v.setBcheck(Objects.nonNull(v.getBcheck()) ? v.getBcheck() : "0");
                                    if("6".equals(v.getBcheck())){
                                        return true;
                                    }
                                    if(flg.equals("5")){
                                        return true;
                                    }else{
                                        return v.getBcheck().equals(flg);
                                    }
                                }
                                return true;
                            }).collect(Collectors.toList());
                })
                .map(list->{
                    //?????????list?????????
                    List<StockKctzVo> resList = list.stream().sorted(
                                    Comparator.comparing(StockKctzVo::getDdate)
                                            .thenComparing(StockKctzVo::getCmakerTime))//??????????????????????????????(status_rank??????)????????????
                            .collect(Collectors.toList());
                    //????????????
                    //??????
                    AtomicReference<Integer> index = new AtomicReference<>(0);
                    resList.forEach(v->{
                        if(Objects.isNull(v.getComcode())){
                            if(Objects.isNull(v.getComcode2())){
                                v.setComcode(v.getComcode3());
                            }else{
                                v.setComcode(v.getComcode2());
                            }
                        }
                        //??????
                        if(Objects.nonNull(v.getBq()) && index.get() != 0){
                            if(!v.getBillStyle().equals("RKTZD")&&!v.getBillStyle().equals("CKTZD")){
                                v.setPrice(Objects.nonNull(v.getIcost()) ? new BigDecimal(v.getIcost()).divide(new BigDecimal(v.getBq()),BigDecimal.ROUND_HALF_UP).toString() : null);
                                v.setPrice1(Objects.nonNull(v.getIcost1()) ? new BigDecimal(v.getIcost1()).divide(new BigDecimal(v.getBq1()),BigDecimal.ROUND_HALF_UP).toString(): null);
                                StockKctzVo stockKctzVo = resList.get(index.get()-1);
                                String bq2 = new BigDecimal(v.getBq()).add(new BigDecimal(stockKctzVo.getBq2())).setScale(2,BigDecimal.ROUND_HALF_UP).toString();
                                String it2 = new BigDecimal(Objects.isNull(v.getIcost())?"0.00":v.getIcost()).add(new BigDecimal(stockKctzVo.getIcost2())).setScale(2,BigDecimal.ROUND_UP).toString();
                                v.setBq2(bq2);
                                v.setIcost2(it2);
                                if("0.00".equals(it2)){
                                    v.setPrice2("0.00");
                                }else{
                                    v.setPrice2(new BigDecimal(it2).divide(new BigDecimal(bq2), 2, BigDecimal.ROUND_HALF_UP).toString());
                                }
                            }else{
                                StockKctzVo stockKctzVo = resList.get(index.get()-1);
                                String it2 = new BigDecimal(Objects.isNull(v.getIcost())?"0.00":v.getIcost()).add(new BigDecimal(stockKctzVo.getIcost2())).setScale(2,BigDecimal.ROUND_UP).toString();
                                v.setIcost2(it2);
                                v.setBq2("0");
                            }


                        }
                        //??????
                        if(Objects.nonNull(v.getBq1()) && index.get() != 0){
                            if(!v.getBillStyle().equals("RKTZD")&&!v.getBillStyle().equals("CKTZD")) {
                                v.setPrice(Objects.nonNull(v.getIcost()) ? new BigDecimal(v.getIcost()).divide(new BigDecimal(v.getBq()),BigDecimal.ROUND_HALF_UP).toString() : null);
                                v.setPrice1(Objects.nonNull(v.getIcost1()) ? new BigDecimal(v.getIcost1()).divide(new BigDecimal(v.getBq1()),BigDecimal.ROUND_HALF_UP).toString(): null);

                                StockKctzVo stockKctzVo = resList.get(index.get()-1);
                                String bq2 = new BigDecimal(stockKctzVo.getBq2()).subtract(new BigDecimal(v.getBq1())).setScale(2,BigDecimal.ROUND_HALF_UP).toString();
                                String it2 = new BigDecimal(stockKctzVo.getIcost2()).subtract( new BigDecimal(Objects.isNull(v.getIcost1())?"0.00":v.getIcost1())).setScale(2,BigDecimal.ROUND_UP).toString();
                                v.setBq2(bq2);
                                v.setIcost2(it2);
                                if("0.00".equals(it2) || "0.00".equals(bq2)){
                                    v.setPrice2("0.00");
                                }else{
                                    v.setPrice2(new BigDecimal(it2).divide(new BigDecimal(bq2), 2, BigDecimal.ROUND_HALF_UP).toString());
                                }
                            }else{
                                StockKctzVo stockKctzVo = resList.get(index.get()-1);
                                String it2 = new BigDecimal(Objects.isNull(v.getIcost())?"0.00":v.getIcost()).add(new BigDecimal(stockKctzVo.getIcost2())).setScale(2,BigDecimal.ROUND_UP).toString();
                                v.setIcost2(it2);
                                v.setBq2("0");
                            }

                        }
                        index.getAndSet(index.get() + 1);
                    });

                    sk.setList(resList);
                    sk.setSize(resList.size());
                    return resList;
                })
                .map(list-> R.ok().setResult(sk));
    }

    //???????????????
    @PostMapping(value = "/xclMake")
    @ApiOperation(value = "???????????????", notes = "???????????????")
    public Mono<R> xclMake(@RequestBody Map maps) {
        String ck = maps.containsKey("ck")?maps.get("ck").toString():"";
        String year = maps.get("year").toString();
        //?????? ??????
        List<StockAccSheetVo> skl = new ArrayList<>();
        StockKctzRestVo sk = new StockKctzRestVo();
        StockKctzVo sv = new StockKctzVo();
        sv.setBillStyle("????????????");
        sv.setDdate("");
        sv.setCcode("");
        sv.setBcheck("5");


        //?????????
        final BigDecimal[] ztr = {BigDecimal.ZERO};
        //?????????
        final BigDecimal[] ztc = {BigDecimal.ZERO};
        //???????????? ??????
        List<StockAccSheetVo> ztrkList = new ArrayList<>();
        List<StockAccSheetVo> ztckList = new ArrayList<>();

        return Mono.just(sv)
                .flatMap(obj->{
                    //??????
                    return  stockBeginBalanceRepository.findAllByIyearAndCk(ck,year)
                            .collectList()
                            .map(wl->{
                                skl.addAll(wl);
                                return skl;
                            });
                })
                .flatMap(obj->{
                    //??????
                    return  warehousingsRepository.findAllByIyearAndCk(ck,year)
                            .collectList()
                            .map(wl->{
                                skl.addAll(wl);
                                return skl;
                            });
                })
                .flatMap(obj->{
                    //??????
                    return saleousingsRepository.findAllByIyearAndCk(ck,year)
                            .collectList()
                            .map(sl->{
                                skl.addAll(sl);
                                return skl;
                            });
                })
                .flatMap(obj->{
                    //????????????
                    return  warehousingsRepository.findAllByIyearAndCkZaitu(ck,year)
                            .collectList()
                            .map(wl->{
                                ztrkList.addAll(wl);
                                return skl;
                            });
                })
                .flatMap(obj->{
                    //????????????
                    return  saleousingsRepository.findAllByIyearAndCkZaitu(ck,year)
                            .collectList()
                            .map(wl->{
                                ztckList.addAll(wl);
                                return skl;
                            });
                })
                .flatMap(list->{
                    //?????????
                    return  stockCurrentstockRepository.findAll()
                            .collectList();
                })
                .map(list->{
                    //?????????????????????????????????
                    list.forEach(s->{
                        //??????+??????
                        List<StockAccSheetVo> ckList = skl.stream().filter(o -> s.getInvCode().equals(o.getCinvode()) && (Objects.nonNull(s.getBatchId()) ? s.getBatchId().equals(o.getBatchid()) : true)  && !"2".equals(o.getTypes())).collect(Collectors.toList());
                        //??????
                        List<StockAccSheetVo> rkList = skl.stream().filter(o -> s.getInvCode().equals(o.getCinvode()) && (Objects.nonNull(s.getBatchId()) ? s.getBatchId().equals(o.getBatchid()) : true)  && "2".equals(o.getTypes())).collect(Collectors.toList());

                        double sumBq = ckList.stream().mapToDouble(v -> {
                            if (Objects.isNull(v.getBq())) return 0.00d;
                            return Double.parseDouble(v.getBq().toString());
                        }).sum();

                        double sumBqrk = rkList.stream().mapToDouble(v -> {
                            if (Objects.isNull(v.getBq())) return 0.00d;
                            return Double.parseDouble(v.getBq().toString());
                        }).sum();

                        //?????????  ??????+??????-??????
                        s.setBaseQuantity(new BigDecimal(sumBq).subtract(new BigDecimal(sumBqrk)));

                    });
                    return list;
                })
                .map(list->{
                    //??????
                    list.forEach(s->{
                        ztr[0] = BigDecimal.ZERO;
                        //??????????????????: ???????????? ???????????? ????????????

                        //????????????-?????????????????????
                        // 
                        //0?????????????????????  ????????? - ????????????
                        List<StockAccSheetVo> qcdhdist = ztrkList.stream().filter(o -> s.getInvCode().equals(o.getCinvode()) && (Objects.nonNull(s.getBatchId()) ? s.getBatchId().equals(o.getBatchid()) : true))
                                .filter(o -> "QC".equals(o.getBillStyle()) && Objects.isNull(o.getSourcecode())).collect(Collectors.toList());
                        double sumQcdhd = qcdhdist.stream().mapToDouble(v -> {
                            if (Objects.isNull(v.getBq())) return 0.00d;
                            return Double.parseDouble(v.getBq().toString());
                        }).sum();

                        double sumLhrk = qcdhdist.stream().mapToDouble(v -> {
                            if (Objects.isNull(v.getIsum())) return 0.00d;
                            return Double.parseDouble(v.getIsum().toString());
                        }).sum();
                        ztr[0] = ztr[0].add(new BigDecimal(sumQcdhd).subtract(new BigDecimal(sumLhrk)));


                        //1.???????????????  ????????????  ?????????
                        List<StockAccSheetVo> bList = ztrkList.stream().filter(o -> s.getInvCode().equals(o.getCinvode()) && (Objects.nonNull(s.getBatchId()) ? s.getBatchId().equals(o.getBatchid()) : true))
                                .filter(o -> "CGDHD".equals(o.getBillStyle()) && Objects.isNull(o.getSourcecode())).collect(Collectors.toList());
                        double sumBqzt = bList.stream().mapToDouble(v -> {
                            if (Objects.isNull(v.getBq())) return 0.00d;
                            return Double.parseDouble(v.getBq().toString());
                        }).sum();
                        ztr[0] = ztr[0].add(new BigDecimal(sumBqzt));

                        //2.?????????????????????   -????????? -?????????
                        //?????????
                        List<StockAccSheetVo> dhList = ztrkList.stream().filter(o -> s.getInvCode().equals(o.getCinvode()) && (Objects.nonNull(s.getBatchId()) ? s.getBatchId().equals(o.getBatchid()) : true) && "CGDHD".equals(o.getBillStyle()) && "1".equals(o.getTypes())).collect(Collectors.toList());
                        //????????? ?????????????????????
                        List<StockAccSheetVo> cgList = ztrkList.stream().filter(o -> s.getInvCode().equals(o.getCinvode()) && (Objects.nonNull(s.getBatchId()) ? s.getBatchId().equals(o.getBatchid()) : true) && "CGRKD".equals(o.getBillStyle()) && Objects.nonNull(o.getSourcecode()) && "CGDHD".equals(o.getSourcetype())).collect(Collectors.toList());
                        //??????????????? ?????????????????????????????????
                        dhList.forEach(g->{
                            List<StockAccSheetVo> collect = cgList.stream().filter(o -> g.getCcode().equals(o.getSourcecode())).collect(Collectors.toList());
                            //???????????????????????? ?????????????????????  ???????????????????????? ??????
                            double sumBqztr = collect.stream().mapToDouble(v -> {
                                if (Objects.isNull(v.getBq())) return 0.00d;
                                return Double.parseDouble(v.getBq().toString());
                            }).sum();

                            //????????????????????????
                            BigDecimal subzt = new BigDecimal(g.getBq()).subtract(new BigDecimal(sumBqztr));
                            ztr[0] = ztr[0].add(subzt);

                            //????????? ??????????????????
                            List<StockAccSheetVo> collect1 = collect.stream().filter(o -> Objects.isNull(o.getTypes()) || "0".equals(o.getTypes())).collect(Collectors.toList());
                            double cBqzt = collect1.stream().mapToDouble(v -> {
                                if (Objects.isNull(v.getBq())) return 0.00d;
                                return Double.parseDouble(v.getBq().toString());
                            }).sum();
                            ztr[0] = ztr[0].add(new BigDecimal(cBqzt));
                        });
                        s.setZtrkQuantityCgdh(ztr[0]);

                        //????????????-?????????????????????
                        List<StockAccSheetVo> cgrkdList = ztrkList.stream().filter(o -> s.getInvCode().equals(o.getCinvode()) && (Objects.nonNull(s.getBatchId()) ? s.getBatchId().equals(o.getBatchid()) : true))
                                .filter(o -> "CGRKD".equals(o.getBillStyle()))
                                .collect(Collectors.toList());
                        double sumCgrkd = cgrkdList.stream().mapToDouble(v -> {
                            if (Objects.isNull(v.getBq())) return 0.00d;
                            return Double.parseDouble(v.getBq().toString());
                        }).sum();
                        s.setZtrkQuantityCgrk(new BigDecimal(sumCgrkd));

                        //????????????-?????????????????????
                        List<StockAccSheetVo> qtrkList = ztrkList.stream().filter(o -> s.getInvCode().equals(o.getCinvode()) && (Objects.nonNull(s.getBatchId()) ? s.getBatchId().equals(o.getBatchid()) : true))
                                .filter(o -> "QTRKD".equals(o.getBillStyle()))
                                .collect(Collectors.toList());
                        double sumQtrk = qtrkList.stream().mapToDouble(v -> {
                            if (Objects.isNull(v.getBq())) return 0.00d;
                            return Double.parseDouble(v.getBq().toString());
                        }).sum();
                        s.setZtrkQuantityQtrk(new BigDecimal(sumQtrk));


                        //??????????????????: ????????? ???????????? ????????????
                        //????????????-????????????????????????
                        //0?????????????????????  ????????? - ????????????
                        ztc[0] = BigDecimal.ZERO;
                        List<StockAccSheetVo> qcxhdist = ztckList.stream().filter(o -> s.getInvCode().equals(o.getCinvode()) && (Objects.nonNull(s.getBatchId()) ? s.getBatchId().equals(o.getBatchid()) : true))
                                .filter(o -> "QC".equals(o.getBillStyle()) && Objects.isNull(o.getSourcecode())).collect(Collectors.toList());
                        double sumQcxhd = qcxhdist.stream().mapToDouble(v -> {
                            if (Objects.isNull(v.getBq())) return 0.00d;
                            return Double.parseDouble(v.getBq().toString());
                        }).sum();

                        double sumLjck = qcxhdist.stream().mapToDouble(v -> {
                            if (Objects.isNull(v.getIsum())) return 0.00d;
                            return Double.parseDouble(v.getIsum().toString());
                        }).sum();
                        ztc[0] = ztc[0].add(new BigDecimal(sumQcxhd).subtract(new BigDecimal(sumLjck)));


                        //1.???????????????  ????????????  ?????????
                        List<StockAccSheetVo> bListck = ztckList.stream().filter(o -> s.getInvCode().equals(o.getCinvode()) && (Objects.nonNull(s.getBatchId()) ? s.getBatchId().equals(o.getBatchid()) : true))
                                .filter(o -> Objects.isNull(o.getSourcecode()) && "XHD".equals(o.getBillStyle())).collect(Collectors.toList());
                        double sumBqztck = bListck.stream().mapToDouble(v -> {
                            if (Objects.isNull(v.getBq())) return 0.00d;
                            return Double.parseDouble(v.getBq().toString());
                        }).sum();
                        ztc[0] = ztc[0].add(new BigDecimal(sumBqztck));
                        //2.?????????????????????   -????????? -?????????
                        //?????????
                        List<StockAccSheetVo> dhListck = ztckList.stream().filter(o -> s.getInvCode().equals(o.getCinvode()) && (Objects.nonNull(s.getBatchId()) ? s.getBatchId().equals(o.getBatchid()) : true) && "XHD".equals(o.getBillStyle()) && "1".equals(o.getTypes())).collect(Collectors.toList());
                        //????????? ?????????????????????
                        List<StockAccSheetVo> cgListck = ztckList.stream().filter(o -> s.getInvCode().equals(o.getCinvode()) && (Objects.nonNull(s.getBatchId()) ? s.getBatchId().equals(o.getBatchid()) : true) && "XSCKD".equals(o.getBillStyle()) && Objects.nonNull(o.getSourcecode()) && "CGDHD".equals(o.getSourcetype())).collect(Collectors.toList());
                        //??????????????? ?????????????????????????????????
                        dhListck.forEach(g->{
                            List<StockAccSheetVo> collect = cgListck.stream().filter(o -> g.getCcode().equals(o.getSourcecode())).collect(Collectors.toList());
                            //???????????????????????? ?????????????????????  ???????????????????????? ??????
                            double sumBqztr = collect.stream().mapToDouble(v -> {
                                if (Objects.isNull(v.getBq())) return 0.00d;
                                return Double.parseDouble(v.getBq().toString());
                            }).sum();

                            //????????????????????????
                            BigDecimal subzt = new BigDecimal(g.getBq()).subtract(new BigDecimal(sumBqztr));
                            ztc[0] = ztc[0].add(subzt);

                            //????????? ??????????????????
                            List<StockAccSheetVo> collect1 = collect.stream().filter(o -> Objects.isNull(o.getTypes()) || "0".equals(o.getTypes())).collect(Collectors.toList());
                            double cBqzt = collect1.stream().mapToDouble(v -> {
                                if (Objects.isNull(v.getBq())) return 0.00d;
                                return Double.parseDouble(v.getBq().toString());
                            }).sum();
                            ztc[0] = ztc[0].add(new BigDecimal(cBqzt));

                        });
                        s.setZtckQuantityXhd(ztc[0]);


                        //????????????-?????????????????????
                        List<StockAccSheetVo> xsckList = ztckList.stream().filter(o -> s.getInvCode().equals(o.getCinvode()) && (Objects.nonNull(s.getBatchId()) ? s.getBatchId().equals(o.getBatchid()) : true))
                                .filter(o -> "XSCKD".equals(o.getBillStyle()) )
                                .collect(Collectors.toList());
                        double sumXsck= xsckList.stream().mapToDouble(v -> {
                            if (Objects.isNull(v.getBq())) return 0.00d;
                            return Double.parseDouble(v.getBq().toString());
                        }).sum();
                        s.setZtckQuantityClly(new BigDecimal(sumXsck));

                        //????????????-?????????????????????
                        List<StockAccSheetVo> qtckList = ztckList.stream().filter(o -> s.getInvCode().equals(o.getCinvode()) && (Objects.nonNull(s.getBatchId()) ? s.getBatchId().equals(o.getBatchid()) : true))
                                .filter(o -> "XSCKD".equals(o.getBillStyle()) )
                                .collect(Collectors.toList());
                        double sumQtck= qtckList.stream().mapToDouble(v -> {
                            if (Objects.isNull(v.getBq())) return 0.00d;
                            return Double.parseDouble(v.getBq().toString());
                        }).sum();
                        s.setZtckQuantityQtck(new BigDecimal(sumQtck));

                        //????????????-?????????????????????
                        List<StockAccSheetVo> cllyckList = ztckList.stream().filter(o -> s.getInvCode().equals(o.getCinvode()) && (Objects.nonNull(s.getBatchId()) ? s.getBatchId().equals(o.getBatchid()) : true))
                                .filter(o -> "CLLYCKD".equals(o.getBillStyle()) )
                                .collect(Collectors.toList());
                        double sumclly= cllyckList.stream().mapToDouble(v -> {
                            if (Objects.isNull(v.getBq())) return 0.00d;
                            return Double.parseDouble(v.getBq().toString());
                        }).sum();
                        s.setZtckQuantityClly(new BigDecimal(sumclly));

                    });
                    return list;
                })
                .flatMap(list->{
                    return  stockCurrentstockRepository.saveAll(list)
                            .collectList()
                            .map(l->{
                                return list.stream().filter(v-> v.getBaseQuantity().compareTo(BigDecimal.ZERO) == 0).collect(Collectors.toList());
                            });
                })
                .flatMap(list->{
                    //??????????????????0????????????
                    return  stockCurrentstockRepository.deleteAll(list)
                            .then(Mono.just(skl));
                })
                .map(list-> R.ok().setResult(sk));
    }


    @GetMapping(value = "/findTask")
    @ApiOperation(value = "???????????????", notes = "???????????????")
    public Mono<R> findTask(){
        return taskRepository.findTaskByCaozuoModule("stock")
                .defaultIfEmpty(new Task())
                .map(o-> R.ok().setResult(o));
    }



}
