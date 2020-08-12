#!/usr/bin/env bash
# 生成dim_hour表所需要的时间数据

# 输出英文的星期和月份
LANG=en_US.UTF-8

start_date=$1
end_date=$2
 
temp_date_full=`date -d "$start_date" +'%F %T'`
temp_start_second=`date -d "$start_date" '+%s'`
temp_end_second=`date -d "$end_date" +%s`

min=1
max=$[($temp_end_second-$temp_start_second)/(60*60)+1]

while [ $min -le $max ]
        do
		
		db_date=`date -d "$temp_date_full" +%F`
		db_hour=`date -d "$temp_date_full" +'%F %H:00:00'`
		year=`date -d "$temp_date_full" +%Y`
		month=`date -d "$temp_date_full" +%m`
		month_name=`date -d "$temp_date_full" +%B`
		day=`date -d "$temp_date_full" +%d`
		hour=`date -d "$temp_date_full" +%H`
		quarter=$[(10#$month-1)/3+1]
		week=`date -d "$temp_date_full" +%W`
		day_name=`date -d "$temp_date_full" +%A`
		w=`date -d "$temp_date_full" +%w`
		weekend_flag="false"
		if [ $w -eq 0 ] || [ $w -eq 6 ]
		then
			weekend_flag="true"
		fi
		dwid=$year$month$day$hour
#                sk=$[10#$year*10000+10#$month*100+10#$day]
		echo ${dwid}","${db_date}","${db_hour}","${year}",""$[10#${month}]"",""$[10#${day}]"",""$[10#${hour}]"","${quarter}",""$[10#${week}]"","${day_name}","${month_name}","${weekend_flag} >> dim_hour.csv
                temp_date_full=`date -d "$temp_date_full 1 hour" +'%F %T'`
                min=$[$min+1]
done
