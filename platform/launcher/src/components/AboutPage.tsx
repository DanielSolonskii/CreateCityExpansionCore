import { ArrowLeft, Blocks, Factory, Heart, Map, Pickaxe, TrainFront, UserRound, UsersRound, Wrench } from "lucide-react";

const principles = [
  { icon: Factory, title: "Промышленный мир", text: "Create лежит в основе производства, автоматизации и развития городов." },
  { icon: TrainFront, title: "Живая логистика", text: "Железные дороги связывают шахты, заводы, рынки и крупные проекты." },
  { icon: Map, title: "Мир игроков", text: "Города, инфраструктура и экономика возникают из решений сообщества." },
];

export function AboutPage({ onBack }: { onBack: () => void }) {
  return (
    <section className="about-page">
      <header className="about-hero">
        <button className="back-button" onClick={onBack}><ArrowLeft size={15} /> На главную</button>
        <div className="about-hero__mark"><Blocks size={31} /><span>R:R</span></div>
        <span className="eyebrow">ИНДУСТРИАЛЬНЫЙ SURVIVAL · MINECRAFT 1.21.1</span>
        <h1>Мир, который<br /><em>строят игроки</em></h1>
        <p>RE:CREATED — сервер о промышленности, расстояниях и инфраструктуре. Здесь завод имеет значение, железная дорога решает настоящую задачу, а новый город начинается не с меню — он начинается со стройки.</p>
        <div className="about-version"><span>СЕЗОН 01</span><i /> NeoForge · Create · EE</div>
      </header>

      <div className="about-principles">
        {principles.map(({ icon: Icon, title, text }, index) => (
          <article key={title}>
            <span className="about-principles__number">0{index + 1}</span>
            <Icon size={22} />
            <h3>{title}</h3>
            <p>{text}</p>
          </article>
        ))}
      </div>

      <section className="team-section">
        <div className="team-section__heading">
          <div><span className="rule" /><span className="eyebrow">ЛЮДИ ЗА ПРОЕКТОМ</span></div>
          <h2>Команда сервера</h2>
          <p>Те, кто превращает идею индустриального мира в пространство для игроков.</p>
        </div>

        <div className="team-grid">
          <article className="team-card team-card--founder">
            <div className="team-card__icon"><UserRound size={24} /></div>
            <div className="team-card__role"><Wrench size={12} /> СОЗДАТЕЛЬ СЕРВЕРА</div>
            <h3>Даниил<br />Солонский</h3>
            <p>Автор идеи и руководитель проекта RE:CREATED.</p>
            <span className="team-card__index">01</span>
          </article>

          <article className="team-card team-card--builders">
            <div className="team-card__icon"><UsersRound size={24} /></div>
            <div className="team-card__role"><Pickaxe size={12} /> BUILDERS</div>
            <div className="builder-person"><h3>Данила Дзержановский</h3><span>DZERZH</span></div>
            <div className="builder-person"><h3>Юрий Пономарёв</h3><span>kastrula8976</span></div>
            <span className="team-card__index">02</span>
          </article>

          <article className="team-card team-card--thanks">
            <div className="team-card__icon"><Heart size={24} /></div>
            <div className="team-card__role"><Heart size={12} /> ОТДЕЛЬНОЕ СПАСИБО</div>
            <h3>Макс</h3>
            <span className="team-nickname">moorguul</span>
            <p>За помощь проекту и вклад в его развитие.</p>
            <span className="team-card__index">03</span>
          </article>
        </div>
      </section>

      <footer className="about-footer">
        <span>RE:CREATED</span>
        <p>Строй. Соединяй. Развивай.</p>
        <span>2026</span>
      </footer>
    </section>
  );
}
