// Reproducible vanilla structure and additive RU/EN resources; no game or economy data.
const fs=require('fs'),path=require('path'),zlib=require('zlib');
const root=path.resolve(__dirname,'..'),base=path.join(root,'src/main/resources/assets/createcityexpansioncore');
const entries={
 'title':['Грузовая точка · настройка в мире','Freight point · world setup'],
 'start':['Настроить точку в мире','Set up a point in the world'],
 'inspect':['Показать оборудование в мире','Inspect equipment in the world'],
 'name_hint':['Название грузовой точки','Freight point name'],
 'select_world':['Выбирать блоки','Select blocks'],
 'build':['Строить / повернуть','Build / rotate'],
 'ponder':['Смотреть Ponder','Watch Ponder'],
 'finish':['Создать точку','Create point'],
 'cancel':['Завершить мастер','End guide'],
 'mode.0':['Отправление','Sending'],
 'mode.1':['Приём','Receiving'],
 'mode.2':['Оба направления','Both directions'],
 'step.0':['1 / 3 · Выберите упаковщик','1 / 3 · Select a Packager'],
 'step.1':['2 / 3 · Выберите почтовый ящик','2 / 3 · Select a Postbox'],
 'step.2':['3 / 3 · Проверьте оборудование','3 / 3 · Review equipment'],
 'explain.0':['Поставьте обычный Packager у склада. Его лицевая сторона должна смотреть от хранилища. Выберите «Выбирать блоки» и нажмите ПКМ по упаковщику.','Place a regular Packager against storage, with its face pointing away from the inventory. Choose Select blocks, then right-click the Packager.'],
 'explain.1':['Выберите Postbox не дальше 16 блоков от упаковщика. Привяжите ящик к настоящей станции средствами Create. Соединение для коробок строится отдельно.','Select a Postbox within 16 blocks of the Packager. Link it to a real station using Create. Build the physical package connection separately.'],
 'explain.2':['Проверьте режим точки и склад, указанный контуром за упаковщиком. Создание требует отдельного подтверждения. При выборе блоков точка ещё не создаётся.','Check the point mode and the outlined inventory behind the Packager. Creating the point needs explicit confirmation; selecting blocks does not register it.'],
 'diagnostics':['Оборудование точки · диагностика','Point equipment · diagnostics'],
 'world_select':['ПКМ — выбрать блок. Shift + ПКМ пустой рукой — параметры. Чтобы строить, включите «Строить / повернуть».','Right-click to select a block. Sneak + right-click with an empty hand for settings. Choose Build / rotate for normal building.'],
 'world_build':['Стройте обычным способом. Shift + ПКМ пустой рукой — вернуться в мастер. Контуры показывают выбранное оборудование.','Build normally. Sneak + right-click with an empty hand to return to the guide. Outlines mark selected equipment.'],
 'warehouse_pos':['Склад за упаковщиком: %s','Inventory behind the Packager: %s'],
 'physical_unverified':['Важно: наличие блоков не подтверждает передачу коробок. Путь конвейера и погрузку проверяйте первой реальной доставкой.','Important: block checks do not prove package transfer. Verify conveyors and train loading with a first real delivery.'],
 'supply_help':['Отправление: подавайте продукцию в склад конвейером, воронкой или другой автоматикой Create. CCEC запрашивает нужный товар у выбранного упаковщика — вручную заполнять каждый заказ не нужно.','Sending: feed storage with belts, funnels or other Create machinery. CCEC requests the assigned item from the selected Packager; each order need not be filled by hand.'],
 'receiver_help':['Приём: используйте сундук, бочку или Contract Intake за Packager. При нехватке места коробка ждёт, товар и деньги заранее не засчитываются.','Receiving: use a chest, barrel or Contract Intake behind the Packager. With insufficient space, the package waits; goods and money are not credited early.'],
 'network_help':['Общий склад Create: подключите складские Packagers через Stock Links. Запрашивайте товары Stock Ticker на отдельный адрес сборки и распаковывайте их в локальный буфер этой точки. CCEC пока не создаёт сетевые заказы автоматически.','Create warehouse: connect stock-side Packagers with Stock Links. Use a Stock Ticker to request goods to a separate collection address and unpack them into this point\'s local buffer. CCEC does not yet create network orders automatically.'],
 'first_batch':['Первая проверка: небольшой договор → выбрать источник, поезд и путь → запустить отправку → дождаться подтверждённого приёма. Автовозврат и отправка всего заказа несколькими рейсами ещё не реализованы.','First test: a small contract → choose source, train and route → start dispatch → verify confirmed receipt. Automatic return and splitting a whole order into multiple trips are not implemented yet.'],
 'no_response':['Мастер не получил ответ сервера. Откройте логистику и повторите запуск.','No server response. Open Logistics and start the guide again.'],
 'permission':['Настраивать точки может владелец или администратор города.','Only a city owner or administrator can set up points.'],
 'registered':['Грузовая точка создана. Назначьте её источником или получателем доставки в логистике.','Freight point created. Assign it as a delivery source or destination in Logistics.'],
 'cancelled':['Мастер закрыт. Существующие точки и доставки сохранены.','Guide closed. Existing points and deliveries are unchanged.'],
 'expired':['Мастер завершён: истекло время, изменились права или измерение. Начните снова у пилона.','Guide ended: time expired, permissions changed, or dimension changed. Start again at the Pylon.'],
 'check.loaded.true':['Оборудование загружено','Equipment loaded'],
 'check.loaded.false':['Территория не загружена — готовность неизвестна','Area unloaded — readiness unknown'],
 'check.packager.true':['Обычный Packager найден','Regular Packager found'],
 'check.packager.false':['Верните обычный Packager; Repackager не подходит','Restore a regular Packager; a Repackager is not supported'],
 'check.inventory.true':['Упаковщик видит склад','Packager detects storage'],
 'check.inventory.false':['Нет склада за упаковщиком — поставьте контейнер или поверните Packager','No storage behind the Packager — place inventory or rotate the Packager'],
 'check.warehouse_loaded.true':['Территория склада загружена','Storage area loaded'],
 'check.warehouse_loaded.false':['Склад в незагруженном чанке — готовность неизвестна','Storage is in an unloaded chunk — readiness unknown'],
 'check.receiver.true':['Приёмный контейнер поддерживается','Receiver container supported'],
 'check.receiver.false':['Для приёма нужны сундук, бочка или Contract Intake за Packager','Receiving needs a chest, barrel or Contract Intake behind the Packager'],
 'check.postbox.true':['Postbox найден','Postbox found'],
 'check.postbox.false':['Верните выбранный Postbox','Restore the selected Postbox'],
 'check.station.true':['Postbox связан со станцией','Postbox linked to a station'],
 'check.station.false':['Привяжите Postbox к железнодорожной станции','Link the Postbox to a train station'],
 'check.range.true':['Расстояние между блоками допустимо','Equipment spacing is valid'],
 'check.range.false':['Postbox должен быть в пределах 16 блоков от Packager','Postbox must be within 16 blocks of the Packager']
};
const shared={
 fg_stock:['Производство пополняет склад. Подавайте товар конвейерами, воронками или другой автоматикой Create — не заполняйте каждый заказ вручную.','Production feeds the warehouse. Use belts, funnels or other Create machinery instead of filling every order by hand.'],
 fg_facing:['Склад находится СЗАДИ упаковщика. Стрелка идёт от хранилища к лицевой стороне Packager.','The warehouse is BEHIND the Packager. The arrow points from the inventory towards the packaging face.'],
 fg_postbox:['Постройте физическую линию передачи коробок от Packager до Postbox. Близость блоков не создаёт соединение.','Build a physical package connection from the Packager to the Postbox. Nearby blocks are not automatically connected.'],
 fg_station:['Привяжите Postbox к настоящей станции средствами Create. Поезду нужен грузовой инвентарь и остановка здесь для обмена посылками.','Link the Postbox to a real train station using Create. The train needs attached cargo inventory and must stop here for mail transfer.'],
 fg_receive:['У получателя CCEC проверяет опечатанные коробки и помещает товар в приёмник. В этой версии нужны сундук, бочка или Contract Intake.','At the destination, CCEC validates sealed packages and moves their goods into the receiver. A chest, barrel or Contract Intake is required for this version.'],
 fg_test:['Начните с небольшого реального договора. Мастер проверяет блоки и адреса; конвейер и передачу поезду подтверждает только настоящий груз. Тестовые товары и CR не создаются.','First deliver a small real order. Setup checks blocks and addresses; only real cargo proves the conveyor and train handoff work. No test goods or CR are created.'],
 fg_link:['Для распределённого склада подключите упаковщики хранилищ через Stock Links к одной логистической сети Create.','For a distributed warehouse, connect stock-side Packagers through Stock Links to the same Create network.'],
 fg_ticker:['Stock Ticker запрашивает товары из сети. Сеть передаёт запросы, а не предметы: коробкам всё ещё нужен физический путь.','The Stock Ticker requests goods from that network. Network signals carry requests, not items: packages still need a physical path.'],
 fg_staging:['Собирайте и распаковывайте складские запросы в локальный буфер за отправляющим Packager CCEC. Используйте отдельный внутренний адрес сборки.','Collect and unpack warehouse requests into a local dispatch buffer behind the CCEC Packager. Use a separate local collection address.'],
 fg_scope:['Сейчас CCEC берёт товар у своего локального Packager. Складское пополнение выполняет Create. CCEC пока не создаёт заказы Stock Ticker и автоматические обратные рейсы.','Current CCEC dispatch draws from its local Packager. Create handles warehouse replenishment; CCEC does not yet generate Stock Ticker network orders or automatic return trips.']
};
const failures={reach:['Подойдите к выбранному блоку ближе.','Move closer to the selected block.'],pick_packager:['Выберите обычный Packager, подключённый к складу.','Select a regular Packager connected to storage.'],pick_postbox:['Теперь выберите Postbox или другой обычный Packager.','Now select a Postbox or another regular Packager.'],incomplete:['Заполните название и исправьте проверки оборудования.','Enter a name and resolve the equipment checks.']};
for(const [idx,locale] of ['ru_ru','en_us'].entries()){
 const file=path.join(base,'lang',locale+'.json');let original=fs.readFileSync(file,'utf8');const parsed=JSON.parse(original),add={};
 for(const [key,pair] of Object.entries(entries))add['gui.createcityexpansioncore.freight_guide.'+key]=pair[idx];
 for(const [key,pair] of Object.entries(shared))add['createcityexpansioncore.ponder.shared.'+key]=pair[idx];
 for(const [key,pair] of Object.entries(failures))add['gui.createcityexpansioncore.freight.'+key]=pair[idx];
 add['createcityexpansioncore.ponder.freight_terminal.header']=idx===0?'Первая грузовая точка CCEC':'Your first CCEC freight terminal';
 add['createcityexpansioncore.ponder.freight_warehouse.header']=idx===0?'Подключение склада Create':'Connecting a Create warehouse';
 const missing=Object.entries(add).filter(([key])=>!(key in parsed));
 if(missing.length){original=original.replace(/\s*}\s*$/,',\n'+missing.map(([k,v])=>'  '+JSON.stringify(k)+': '+JSON.stringify(v)).join(',\n')+'\n}\n');fs.writeFileSync(file,original);}
}
// Keep the shipped equipment and scripted positions in one deterministic generator.
require('child_process').execFileSync('python',['-X','utf8','tools/freight-ponder-v2.py'],{stdio:'inherit'});
