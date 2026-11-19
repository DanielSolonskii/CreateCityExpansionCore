import { ArrowLeft, Blocks, Factory, Map, TrainFront, UserRound } from "lucide-react";

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
          {[
            ["Максим Александров", "Разработчик / администратор"],
            ["Данила Дзержановский", "Разработчик / администратор"],
            ["Даниил Солонский", "Разработчик / директор"],
          ].map(([name, role]) => (
            <article className="team-card" key={name}>
              <div className="team-card__icon"><UserRound size={24} /></div>
              <h3>{name}</h3>
              <p>{role}</p>
            </article>
          ))}
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
