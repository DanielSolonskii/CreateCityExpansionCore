// Additive, repeatable localization for endpoint / regional cross-docking gameplay.
const fs=require('fs'),path=require('path');const base=path.resolve(__dirname,'../src/main/resources/assets/createcityexpansioncore/lang');
const text={
 'kind.endpoint':['Точка отправления / приёма','Sending / receiving point'],
 'kind.hub':['Логистический центр','Logistics centre'],
 'structures.0':['Все: точки и логистические центры','All: points and logistics centres'],
 'structures.1':['Только конечные точки','Endpoints only'],
 'structures.2':['Только логистические центры','Logistics centres only'],
 'endpoint_help':['Конечная точка работает с товарами своего склада. Можно построить и вне города, на незанятой территории; привата это не создаёт.','An endpoint works with goods in its own warehouse. It may stand in unclaimed wilderness; this does not create land protection.'],
 'hub_help':['Центр хранит и пересылает целые опечатанные коробки. Буфер: бочка или Item Vault за Packager; весь Vault и Postbox должны помещаться в один чанк. После регистрации буфер закреплён за выбранным местом. Транзит не распаковывайте. Поезда обслуживают расписания Create.','A centre stores and forwards whole sealed packages. Buffer: a barrel or Item Vault behind the Packager; the entire vault and Postbox must fit in one chunk. Registration pins the buffer location. Do not unpack transit cargo. Trains use Create schedules.'],
 'make_hub':['Преобразовать в логистический центр','Convert to logistics centre'],
 'make_endpoint':['Преобразовать в конечную точку','Convert to endpoint'],
 'open_public':['Разрешить транзит другим городам','Allow transit for other cities'],
 'close_public':['Закрыть новые заявки других городов','Close new bookings from other cities'],
 'pause_forward':['Приостановить пересылку','Pause forwarding'],
 'resume_forward':['Возобновить пересылку','Resume forwarding'],
 'public':['Открыт для транзита других городов','Open to transit from other cities'],
 'private':['Только для города-оператора','Operator city only'],
 'occupancy':['В буфере: %s коробок · %s ячеек','Buffer: %s packages · %s slots'],
 'status.idle':['Ожидает посылки или поезд','Waiting for packages or a train'],
 'status.unavailable':['Нет актуальных данных / проверьте буфер и Postbox','No current data / check the buffer and Postbox'],
 'status.paused':['Пересылка приостановлена; прибывающие коробки сохраняются','Forwarding paused; arriving packages are retained'],
 'status.next_unavailable':['Следующий узел удалён или отключён','Next node is removed or disabled'],
 'status.forwarded':['Коробки подготовлены к следующему плечу','Packages prepared for the next leg'],
 'status.stored':['Посылки поступили в транзитный буфер','Packages entered the transit buffer'],
 'status.outbox_full':['Почтовый ящик заполнен — ожидаем погрузку','Postbox full — waiting for loading'],
 'status.buffer_full':['Транзитная бочка заполнена','Transit barrel full'],
 'regional':['Региональная пересылка · расписания Create','Regional forwarding · Create schedules'],
 'direct':['Прямая доставка · назначенный поезд','Direct delivery · assigned train'],
 'regional_help':['CCEC упаковывает товар и меняет адрес на каждом центре. Настройте физические пути и циклические расписания Create между узлами: прибытие, ожидание обмена посылками, следующий узел и возврат. Используйте отдельные станции для разных направлений: Postbox может загрузить коробки в любой остановившийся поезд. Плата за рельсы — по существующему учёту зарегистрированного поезда; товарный резерв выплачивается только у конечного получателя.','CCEC packs goods and changes their address at each centre. Build physical connections and cyclic Create schedules between nodes: arrive, wait for package exchange, next node, then return. Use separate stations for different directions: a Postbox can load packages into any stopping train. Track tolls use existing registered-train accounting; goods escrow settles only at the final receiver.'],
 'edit_route':['Способ доставки и промежуточные центры','Delivery mode and intermediate centres'],
 'route_title':['Маршрут доставки','Delivery itinerary'],
 'route_help':['Выбор строк меняет только черновик. Сохраните маршрут до запуска отправки. До четырёх промежуточных центров; повторы и конечная точка в списке запрещены.','Selecting rows edits the draft only. Save the itinerary before starting dispatch. Up to four intermediate centres; duplicates and the final receiver are not allowed in the list.'],
 'add_hop':['Добавить центр','Add centre'],
 'remove_hop':['Убрать · шаг %s','Remove · stop %s'],
 'save_route':['Сохранить способ и маршрут','Save mode and itinerary'],
 'result.ok':['Настройки логистики сохранены.','Logistics settings saved.'],
 'result.permission':['Управлять узлом или поставкой может администратор города-оператора.','Only the operator city administrator may manage this node or delivery.'],
 'result.busy':['Настройка выполняется. Повторите через мгновение.','A setting is being applied. Retry in a moment.'],
 'result.hub_required':['Эта настройка доступна только логистическому центру.','This setting requires a logistics centre.'],
 'result.route_locked':['Приостановите отправку. Маршрут можно менять только когда нет активных посылок.','Pause dispatch. The itinerary can change only when no packages are active.'],
 'result.route_invalid':['Проверьте центры и связь станций в одной железнодорожной сети.','Check the centres and station connectivity within one railway graph.'],
 'result.hub_denied':['Центр отключён, закрыт для вашего города или находится в другом измерении.','A centre is disabled, closed to your city, or in another dimension.'],
 'result.hub_buffer':['Нужен режим «Оба направления» и бочка либо Item Vault за Packager. Все блоки буфера и Postbox должны находиться в одном чанке.','Use Both directions and a barrel or Item Vault behind the Packager. All buffer blocks and the Postbox must be in one chunk.'],
 'result.hub_in_use':['Через центр идут активные посылки. Остановите новые отправления и дождитесь завершения.','Active packages use this centre. Stop new dispatches and wait for completion.']
};
for(const [index,locale] of ['ru_ru','en_us'].entries()){
 const file=path.join(base,locale+'.json');let original=fs.readFileSync(file,'utf8');const parsed=JSON.parse(original), values={};
 for(const [key,pair] of Object.entries(text))values['gui.createcityexpansioncore.freight_network.'+key]=pair[index];
 values['gui.createcityexpansioncore.world_map.legend.freight_hub']=index===0?'Логистический центр':'Logistics centre';
 values['gui.createcityexpansioncore.world_map.legend.freight_link']=index===0?'Схема поставки · не путь по рельсам':'Delivery schematic · not track geometry';
 values['gui.createcityexpansioncore.freight.network_schedule']=index===0?'Региональная пересылка использует расписания Create.':'Regional forwarding uses Create schedules.';
 values['gui.createcityexpansioncore.freight.hub_rebind']=index===0?'Сначала завершите транзит и преобразуйте центр в конечную точку. Буфер центра нельзя молча перенести перепривязкой.':'Finish transit and convert the centre to an endpoint first. Rebinding must not silently relocate its cargo buffer.';
 values['gui.createcityexpansioncore.freight.territory_conflict']=index===0?'Оборудование оказалось на территории другого города':'Equipment is now on another city\'s territory';
 values['status.createcityexpansioncore.unified.point_territory_conflict.title']=index===0?'Конфликт территории грузовой точки':'Freight point territory conflict';
 values['status.createcityexpansioncore.unified.point_territory_conflict.reason']=index===0?'Оборудование находится на территории другого города.':'The equipment is on another city\'s territory.';
 values['status.createcityexpansioncore.unified.point_territory_conflict.hint']=index===0?'Перепривяжите точку на своей или незанятой территории.':'Rebind the point within your own or unclaimed territory.';
 values['gui.createcityexpansioncore.freight_guide.receiver_help']=index===0?'Приём: используйте сундук, бочку, Item Vault Create или Contract Intake за Packager. При нехватке места коробка ждёт; товар и деньги заранее не засчитываются.':'Receiving: use a chest, barrel, Create Item Vault or Contract Intake behind the Packager. With insufficient space, the package waits; goods and money are not credited early.';
 values['gui.createcityexpansioncore.freight_guide.check.receiver.false']=index===0?'Для приёма нужны сундук, бочка, Item Vault или Contract Intake за Packager':'Receiving needs a chest, barrel, Item Vault or Contract Intake behind the Packager';
 values['createcityexpansioncore.ponder.shared.fg_receive']=index===0?'У получателя CCEC проверяет опечатанные коробки и помещает товар в приёмник: сундук, бочку, Item Vault Create или Contract Intake.':'At the destination, CCEC validates sealed packages and moves goods into a chest, barrel, Create Item Vault or Contract Intake.';
 values['gui.createcityexpansioncore.freight_guide.first_batch']=index===0?'Первая проверка: небольшой договор → выбрать источник и способ доставки → отправить → дождаться приёма. Прямая доставка использует назначенный поезд; для циклических перевозок выберите региональную пересылку и настройте расписания Create.':'First test: a small contract → choose source and delivery mode → dispatch → verify receipt. Direct delivery uses an assigned train; for cyclic transport choose regional forwarding and configure Create schedules.';
 const missing={};
 for(const [key,value] of Object.entries(values)){
   if(!(key in parsed))missing[key]=value;
   else if(parsed[key]!==value){const escaped=key.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');original=original.replace(new RegExp('(^\\s*"'+escaped+'":\\s*)"(?:\\\\.|[^"\\\\])*"','m'),(_m,prefix)=>prefix+JSON.stringify(value));}
 }
 const add=Object.entries(missing);if(add.length)original=original.replace(/\s*}\s*$/,',\n'+add.map(([k,v])=>'  '+JSON.stringify(k)+': '+JSON.stringify(v)).join(',\n')+'\n}\n');
 JSON.parse(original);fs.writeFileSync(file,original);
}
console.log('Regional Freight RU/EN resources updated');
