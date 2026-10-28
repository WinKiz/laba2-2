from pathlib import Path
import csv, statistics, hashlib, html
ROOT=Path(__file__).resolve().parent
R=ROOT/'results'
SAFE=['synchronized-map','concurrent-merge','long-adder','local-maps']
all_summaries={}
for corpus in ['small','medium','large']:
    folder=R/corpus
    rows=list(csv.DictReader((folder/'measurements.csv').open(encoding='utf-8')))
    groups={}
    for row in rows:
        if row['phase']=='measurement': groups.setdefault((row['variant'],int(row['threads'])),[]).append(row)
    summary=[]
    base=statistics.median(float(r['time_seconds']) for r in groups[('sequential',1)])
    for (name,p), group in groups.items():
        t=statistics.median(float(r['time_seconds']) for r in group)
        ok=sum(r['correct']=='true' for r in group)
        summary.append(dict(variant=name,threads=p,median_seconds=t,correct_runs=ok,runs=len(group),speedup=base/t,efficiency_percent=100*base/t/p))
    all_summaries[corpus]=summary
    with (folder/'summary.csv').open('w',newline='',encoding='utf-8') as f:
        w=csv.DictWriter(f,fieldnames=summary[0].keys()); w.writeheader(); w.writerows(summary)
    table=['# '+corpus,'','Медиана трёх измерений после двух прогревов каждой конфигурации. S=Tseq/Tp; E=100S/p. Для ошибочных вариантов показатели не являются ускорением решения задачи.','','| Вариант | Потоки | Медиана, с | Верных запусков | S | E, % |','|---|---:|---:|---:|---:|---:|']
    for x in summary:
        safe=x['variant'] in SAFE+['sequential']
        table.append(f"| {x['variant']} | {x['threads']} | {x['median_seconds']:.6f} | {x['correct_runs']}/{x['runs']} | {x['speedup']:.3f} | {x['efficiency_percent']:.2f} |" if safe else f"| {x['variant']} | {x['threads']} | {x['median_seconds']:.6f} | {x['correct_runs']}/{x['runs']} | — | — |")
    (folder/'timings.md').write_text('\n'.join(table)+'\n',encoding='utf-8')
    for metric,label in [('time','Time, seconds'),('speedup','Speedup Tseq/Tp'),('efficiency','Efficiency, %')]:
        field={'time':'median_seconds','speedup':'speedup','efficiency':'efficiency_percent'}[metric]
        ymax=max(x[field] for x in summary if x['variant'] in SAFE)*1.12
        out=['<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="650" viewBox="0 0 1000 650">','<rect width="1000" height="650" fill="white"/>',f'<text x="70" y="35" font-size="24">{corpus}: {label}</text>']
        def text(x,y,t,size=14): return f'<text x="{x}" y="{y}" font-size="{size}" font-family="sans-serif">{html.escape(str(t))}</text>'
        for i in range(6):
            y=540-i*90; v=ymax*i/5
            out.extend([f'<path d="M80 {y}H940" stroke="#ddd"/>',text(5,y+4,f'{v:.3g}')])
        ps=[1,2,4,8,16,32,64]
        for i,p in enumerate(ps): out.append(text(80+i*140,565,p))
        out.append(text(420,600,'Threads (log2 spacing)'))
        for j,name in enumerate(SAFE):
            color=['#2563eb','#d97706','#16a34a','#9333ea'][j]
            data=[x for x in summary if x['variant']==name]
            points=' '.join(f"{80+ps.index(x['threads'])*140},{540-450*x[field]/ymax}" for x in data)
            out.append(f'<polyline points="{points}" fill="none" stroke="{color}" stroke-width="3"/>')
            out.append(f'<text x="{80+j*230}" y="630" font-size="14" fill="{color}" font-family="sans-serif">{name}</text>')
        out.append('</svg>'); (folder/(metric+'.svg')).write_text('\n'.join(out),encoding='utf-8')
# Consolidated raw data retain corpus labels.
with (R/'measurements.csv').open('w',encoding='utf-8',newline='') as f:
    writer=None
    for corpus in all_summaries:
        for row in csv.DictReader((R/corpus/'measurements.csv').open(encoding='utf-8')):
            row={'corpus':corpus,**row}
            if writer is None: writer=csv.DictWriter(f,fieldnames=row.keys()); writer.writeheader()
            writer.writerow(row)
meta=['# Корпуса и контрольные суммы','', 'SHA-256 каждого файла позволяет проверить неизменность входа. Данные исключены из Git, seed и параметры сохранены здесь.','']
for corpus in all_summaries:
    d=ROOT/'generated-data'/corpus
    meta += ['## '+corpus,'','```text',(d/'metadata.txt').read_text().strip(),'```','']
    for f in sorted(d.iterdir()):
        digest=hashlib.sha256(f.read_bytes()).hexdigest()
        meta.append(f'`{corpus}/{f.name}` `{digest}`')
(R/'corpora.md').write_text('\n'.join(meta),encoding='utf-8')
large=all_summaries['large']; safe=[x for x in large if x['variant'] in SAFE and x['correct_runs']==x['runs']]
best=min(safe,key=lambda x:x['median_seconds']); baseline=next(x for x in large if x['variant']=='sequential')
report=['# Отчёт и ответы на вопросы лабораторной','', '## Условия','', (R/'large/environment.txt').read_text(), 'CPU: '+__import__('os').environ.get('PROCESSOR_IDENTIFIER','unknown')+'. Пул платформенных потоков; -Xms256m -Xmx1g. Java 23.0.2, компиляция --release 21. Размеры: 128 файлов × 100/1000/10000 слов, словарь 500. Полный размер примера 32 млн слов не запускался. Корпус не менялся внутри серии. Параметры и контрольные суммы: corpora.md.', '', 'Два прогрева и три измерения для каждой конфигурации, медиана. Эталон загружается до таймера. В таймер входит весь analyze, включая чтение, создание пула и слияние. Корректность проверяется после каждого прогрева и измерения. Порядок вариантов фиксирован; кэш ОС прогрет, независимых форков JVM нет. Фон ОС не контролируется полностью. Поэтому небольшие различия и точный оптимум требуют повторной серии; это учебные измерения, не универсальная характеристика алгоритмов.','', '## Ответы 1–11','']
report += ['### 1. Корректен ли общий HashMap?','Нет. Одновременное изменение не защищено, возможны гонки структуры и потеря обновлений. Проявление зависит от расписания, успешный опыт не доказывает безопасность.']
for name in ['hashmap','concurrent-get-put']:
    report.append(name+': '+ '; '.join(f"p={x['threads']}: {x['correct_runs']}/{x['runs']} верных" for x in large if x['variant']==name)+'.')
report += ['### 2. Производительность synchronizedMap','merge целиком защищён общим монитором: корректно, но обновления сериализуются. Фактические медианы: '+ '; '.join(f"p={x['threads']}: {x['median_seconds']:.6f} с" for x in large if x['variant']=='synchronized-map')+'.', '### 3. Почему CHM get/put ошибочен?','Методы защищены по отдельности, но два потока могут прочитать 10 и оба записать 11 вместо 12. Структура остаётся потокобезопасной, логический подсчёт — нет.','### 4. Почему merge подходит?','CHM выполняет обновление данного ключа атомарно; при отсутствии записывает 1, иначе суммирует со старым значением. В synchronizedMap весь merge выполняется под общим монитором. Это гарантия конкретных реализаций, не любого Map.merge.','### 5. Отличие CHM и synchronizedMap','Обёртка использует общий монитор для операций. CHM допускает конкурентные обновления и обычно неблокирующие чтения; при конфликте корзин/ключей остаётся конкуренция. Не следует утверждать, что у современного CHM всегда отдельный замок для каждого ключа.','### 6. Как влияет LongAdder?','Распределяет конкурентные увеличения по внутренним ячейкам. Это может снизить конкуренцию ценой памяти; итог sum здесь берётся после завершения задач. Медианы: '+ '; '.join(f"p={x['threads']}: {x['median_seconds']:.6f} с" for x in large if x['variant']=='long-adder')+'.','### 7. Локальные словари','Общих изменений во время подсчёта нет. Накладные расходы — p словарей и последовательное слияние до O(pV). Медианы: '+ '; '.join(f"p={x['threads']}: {x['median_seconds']:.6f} с" for x in large if x['variant']=='local-maps')+'.','### 8. Самая быстрая корректная реализация',f"На большом корпусе среди многопоточных: {best['variant']}, {best['threads']} потоков, {best['median_seconds']:.6f} с. Последовательная: {baseline['median_seconds']:.6f} с. Ускорение {best['speedup']:.3f}, эффективность {best['efficiency_percent']:.2f}%.",'### 9. Где минимальное время?',f"В исследованной сетке большого корпуса минимум корректных многопоточных — {best['threads']} потоков у {best['variant']}. Это минимум данной серии, не доказательство глобального оптимума."]
for name in SAFE:
    b=min((x for x in safe if x['variant']==name),key=lambda x:x['median_seconds']); report.append(f"{name}: p={b['threads']}, {b['median_seconds']:.6f} с.")
report += ['### 10. Почему нет пропорционального ускорения?','Синхронизация, последовательные стадии чтения/слияния, создание и планирование потоков, переключения контекста, ограниченная пропускная способность памяти и файлового ввода. Наблюдаемые колебания согласуются с этими причинами, но без профилирования нельзя назвать одну доказанной. Закон Амдала ограничивает ускорение последовательной долей.','### 11. Влияние размера задачи','Чем больше полезной работы на задачу, тем легче компенсировать создание пула и прочие расходы. На очень малом корпусе накладные расходы и шум особенно существенны. Для разных размеров распределения случайно различны: сравнения вариантов внутри корпуса честные, а чистый эффект размера немного смешан с распределением.','', '| Корпус | Слов | Последовательно, с | Лучший корректный многопоточный | p | Время, с | S |','|---|---:|---:|---|---:|---:|---:|']
for corpus,summary in all_summaries.items():
    b=min((x for x in summary if x['variant'] in SAFE),key=lambda x:x['median_seconds']); seq=next(x for x in summary if x['variant']=='sequential')
    report.append(f"| {corpus} | {dict(small=12800,medium=128000,large=1280000)[corpus]} | {seq['median_seconds']:.6f} | {b['variant']} | {b['threads']} | {b['median_seconds']:.6f} | {b['speedup']:.3f} |")
report += ['', '## Проверка и сдача','', 'Безопасные варианты должны иметь 3/3 верных измерения и верные прогревы. SelfTest дополнительно проверяет Unicode, регистр, пунктуацию, пустой файл, несколько файлов, число потоков больше числа файлов, лишние/отсутствующие ключи, неверный счётчик, передачу ошибки работника, детерминированный lost update на CHM и synchronizedMap. У намеренно ошибочных алгоритмов проверяется фактический исход, а не ожидается обязательная ошибка каждого запуска.', '', 'Таблицы: small/timings.md, medium/timings.md, large/timings.md. Полные исходные измерения: measurements.csv (включает прогревы и конкретные несовпадения). Графики PNG и SVG для каждого корпуса: time, speedup, efficiency; в корне results копии большого корпуса. Ускорения на графиках только для корректных алгоритмов.','', 'Репозиторий проекта: https://github.com/WinKiz/laba2-2. Исходные корпуса и target исключены из Git; генератор и параметры позволяют воспроизвести данные.']
(R/'conclusions.md').write_text('\n\n'.join(report),encoding='utf-8')
for name in ['timings.md','time.svg','speedup.svg','efficiency.svg']:
    (R/name).write_bytes((R/'large'/name).read_bytes())
print('Reports generated; best:',best)
