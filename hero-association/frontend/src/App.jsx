import { useState } from 'react'
import './App.css'

const navigation = [
  { id: 'overview', label: 'Overview', icon: '◇' },
  { id: 'heroes', label: 'Heroes', icon: '♙' },
  { id: 'quests', label: 'Quests', icon: '⚔' },
  { id: 'agency', label: 'Agency', icon: '⌂' },
  { id: 'market', label: 'Market', icon: '⇄' },
  { id: 'feed', label: 'Feed', icon: '◌' },
]

const heroes = [
  { name: 'Brom Ironwall', alias: 'Ironwall', role: 'Warrior', level: 8, stamina: 82, color: 'gold', training: 'Melee 16 · Shield 14' },
  { name: 'Elara Moonweaver', alias: 'Moonweaver', role: 'Mage', level: 7, stamina: 64, color: 'violet', training: 'Magic 18' },
  { name: 'Kael Swiftarrow', alias: 'Swiftarrow', role: 'Archer', level: 6, stamina: 91, color: 'teal', training: 'Distance 17' },
]

const metrics = [
  { label: 'Gold', value: '2,480', detail: '+240 this week', icon: 'G' },
  { label: 'Reputation', value: '340', detail: 'Rank: Trusted', icon: 'R' },
  { label: 'Party capacity', value: '71 / 120', detail: '49 remaining', icon: 'C' },
]

const upgrades = [
  { name: 'Training', level: 4, detail: 'Improves available hero training.' },
  { name: 'Rest', level: 3, detail: 'Restores stamina faster.' },
  { name: 'Size', level: 4, detail: 'Room for heroes and facilities.' },
  { name: 'Reputation', level: 3, detail: 'Unlocks better opportunities.' },
  { name: 'Medical', level: 2, detail: 'Helps heroes recover after quests.' },
  { name: 'Intelligence', level: 3, detail: 'Reveals quest risks and rewards.' },
]

function HeroAvatar({ hero, size = 'normal' }) {
  return <div className={`hero-avatar hero-avatar--${hero.color} hero-avatar--${size}`} aria-hidden="true">{hero.alias.slice(0, 1)}</div>
}

function StaminaBar({ value }) {
  return (
    <div className="stamina" aria-label={`${value}% stamina`}>
      <div className="stamina__track"><span className="stamina__value" style={{ width: `${value}%` }} /></div>
      <span>{value}%</span>
    </div>
  )
}

function PageHeading({ eyebrow, title, description, action }) {
  return (
    <header className="page-heading">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        <p className="page-heading__description">{description}</p>
      </div>
      {action}
    </header>
  )
}

function Overview({ onNavigate }) {
  return (
    <>
      <PageHeading
        eyebrow="Dawnwatch Agency"
        title="Your agency is ready"
        description="Prepare your heroes, send a party on a quest, and grow your name."
        action={<button className="button button--primary" type="button" onClick={() => onNavigate('quests')}>View quests</button>}
      />
      <section className="metrics" aria-label="Agency summary">
        {metrics.map((metric) => (
          <article className="metric-card" key={metric.label}>
            <span className="metric-card__icon" aria-hidden="true">{metric.icon}</span>
            <div><p>{metric.label}</p><strong>{metric.value}</strong><small>{metric.detail}</small></div>
          </article>
        ))}
      </section>
      <section className="dashboard-grid">
        <article className="panel active-quest">
          <div className="panel__header">
            <div><p className="eyebrow">Active quest</p><h2>Trolls at Broken Pass</h2></div>
            <span className="status status--progress">In progress</span>
          </div>
          <p className="active-quest__description">Clear the trade route before the caravan arrives.</p>
          <div className="quest-progress">
            <div className="quest-progress__labels"><span>Creatures defeated</span><strong>7 / 10</strong></div>
            <div className="quest-progress__track"><span /></div>
          </div>
          <div className="active-quest__footer">
            <div className="party-avatars" aria-label="Quest party">
              {heroes.map((hero) => <HeroAvatar hero={hero} size="small" key={hero.alias} />)}
              <span>3 heroes</span>
            </div>
            <div className="quest-time"><span>Estimated time</span><strong>18m remaining</strong></div>
          </div>
        </article>
        <article className="panel agency-level">
          <div className="panel__header">
            <div><p className="eyebrow">Agency progress</p><h2>Agency level 4</h2></div>
            <button className="text-button" type="button" onClick={() => onNavigate('agency')}>Manage</button>
          </div>
          <div className="level-orb" aria-label="Agency level 4">4</div>
          <p>Next level unlocks more training and rest upgrades.</p>
          <div className="level-progress"><span style={{ width: '68%' }} /></div>
          <small>1,360 / 2,000 agency experience</small>
        </article>
      </section>
      <section className="panel roster-panel">
        <div className="panel__header">
          <div><p className="eyebrow">Your roster</p><h2>Heroes</h2></div>
          <button className="text-button" type="button" onClick={() => onNavigate('heroes')}>View all</button>
        </div>
        <div className="hero-list">
          {heroes.map((hero) => (
            <article className="hero-row" key={hero.alias}>
              <HeroAvatar hero={hero} />
              <div className="hero-row__identity"><strong>{hero.alias}</strong><span>{hero.role} · Level {hero.level}</span></div>
              <div className="hero-row__training">{hero.training}</div>
              <StaminaBar value={hero.stamina} />
            </article>
          ))}
        </div>
      </section>
    </>
  )
}

function Heroes() {
  return (
    <>
      <PageHeading eyebrow="Dawnwatch Agency" title="Heroes" description="A small roster, ready for its next challenge." action={<button className="button button--primary" type="button">Recruit hero</button>} />
      <section className="hero-cards" aria-label="Hero roster">
        {heroes.map((hero) => (
          <article className="panel hero-card" key={hero.alias}>
            <HeroAvatar hero={hero} size="large" />
            <div><p className="eyebrow">{hero.role}</p><h2>{hero.alias}</h2><p className="hero-card__name">{hero.name} · Level {hero.level}</p></div>
            <div className="hero-card__details"><span>Training</span><strong>{hero.training}</strong></div>
            <StaminaBar value={hero.stamina} />
          </article>
        ))}
      </section>
    </>
  )
}

function Quests() {
  return (
    <>
      <PageHeading eyebrow="Quest board" title="Quests" description="Choose a party that can finish the job and return safely." action={<button className="button button--primary" type="button">Find quests</button>} />
      <section className="quest-list">
        <article className="panel quest-card quest-card--active">
          <div><span className="status status--progress">In progress</span><h2>Trolls at Broken Pass</h2><p>Defeat 10 trolls to reopen the northern trade route.</p></div>
          <div className="quest-card__facts"><span><b>7 / 10</b> defeated</span><span><b>18m</b> remaining</span><span><b>120</b> gold reward</span></div>
        </article>
        <article className="panel quest-card">
          <div><span className="status">Available</span><h2>Lost Courier</h2><p>Find the missing courier in the old forest.</p></div>
          <div className="quest-card__facts"><span><b>1–2</b> heroes</span><span><b>30m</b> estimated</span><span><b>85</b> gold reward</span></div>
        </article>
      </section>
    </>
  )
}

function Agency() {
  return (
    <>
      <PageHeading eyebrow="Dawnwatch Agency" title="Agency upgrades" description="Agency level caps each specialized upgrade. You choose which areas to prioritize." action={<button className="button button--primary" type="button">Upgrade agency</button>} />
      <section className="upgrade-grid" aria-label="Agency upgrade levels">
        {upgrades.map((upgrade) => (
          <article className="panel upgrade-card" key={upgrade.name}>
            <div className="upgrade-card__level">{upgrade.level}</div>
            <div><h2>{upgrade.name} level</h2><p>{upgrade.detail}</p></div>
            <button className="text-button" type="button">Details</button>
          </article>
        ))}
      </section>
    </>
  )
}

function Market() {
  return (
    <>
      <PageHeading eyebrow="Community exchange" title="Market" description="Place buy and sell offers. A 10% fee applies when offers match." action={<button className="button button--primary" type="button">Create offer</button>} />
      <section className="market-grid">
        <article className="panel offer-panel">
          <div className="panel__header"><h2>Buy offers</h2><span className="status">4 offers</span></div>
          <div className="offer-row"><span>Magic Crystal</span><strong>98 gold</strong><small>12 available</small></div>
          <div className="offer-row"><span>Iron Ingot</span><strong>16 gold</strong><small>48 available</small></div>
        </article>
        <article className="panel offer-panel">
          <div className="panel__header"><h2>Sell offers</h2><span className="status">6 offers</span></div>
          <div className="offer-row"><span>Magic Crystal</span><strong>105 gold</strong><small>8 available</small></div>
          <div className="offer-row"><span>Iron Ingot</span><strong>21 gold</strong><small>31 available</small></div>
        </article>
      </section>
    </>
  )
}

function Feed() {
  return (
    <>
      <PageHeading eyebrow="Community" title="Feed" description="Updates from agencies, managers, and the heroes who make their names known." action={<button className="button button--primary" type="button">Write post</button>} />
      <section className="feed-list">
        <article className="panel feed-post">
          <div className="feed-post__author"><span className="feed-post__mark">D</span><div><strong>Dawnwatch Agency</strong><small>12 minutes ago</small></div></div>
          <p>The party has reached Broken Pass. The road will be open again soon.</p>
          <div className="feed-post__item">Quest in progress · Trolls at Broken Pass</div>
        </article>
        <article className="panel feed-post">
          <div className="feed-post__author"><HeroAvatar hero={heroes[1]} size="small" /><div><strong>Elara Moonweaver</strong><small>1 hour ago</small></div></div>
          <p>Rested, prepared, and ready for whatever waits beyond the pass.</p>
        </article>
      </section>
    </>
  )
}

function App() {
  const [activePage, setActivePage] = useState('overview')
  const pages = {
    overview: <Overview onNavigate={setActivePage} />,
    heroes: <Heroes />,
    quests: <Quests />,
    agency: <Agency />,
    market: <Market />,
    feed: <Feed />,
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#overview" onClick={() => setActivePage('overview')}><span className="brand__mark" aria-hidden="true">H</span><span>Hero Association</span></a>
        <nav className="navigation" aria-label="Main navigation">
          {navigation.map((item) => (
            <button className={`navigation__item ${activePage === item.id ? 'navigation__item--active' : ''}`} key={item.id} type="button" aria-current={activePage === item.id ? 'page' : undefined} onClick={() => setActivePage(item.id)}>
              <span aria-hidden="true">{item.icon}</span>{item.label}
            </button>
          ))}
        </nav>
        <div className="sidebar__bottom"><div className="player-card"><span className="player-card__avatar">T</span><span><strong>Tiago</strong><small>Agency leader</small></span></div></div>
      </aside>
      <main className="main-content"><div className="main-content__inner">{pages[activePage]}</div></main>
    </div>
  )
}

export default App
