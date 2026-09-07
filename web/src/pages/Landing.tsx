import Nav from '../components/landing/Nav'
import Hero from '../components/landing/Hero'
import { PinnedLifecycleStage } from '../components/landing/PinnedLifecycleStage'
import FeaturesSection from '../components/landing/FeaturesSection'
import AISection from '../components/landing/AISection'
import GoalsSection from '../components/landing/GoalsSection'
import SharedSection from '../components/landing/SharedSection'
import DownloadSection from '../components/landing/DownloadSection'
import Footer from '../components/landing/Footer'
import { AmbientAurora } from '../components/ui/AmbientAurora'
import { ScrollProgress } from '../components/ui/ScrollProgress'

export default function Landing() {
  return (
    <>
      <ScrollProgress />
      <AmbientAurora />
      <Nav />
      <main style={{ position: 'relative', zIndex: 1 }}>
        <Hero />
        <PinnedLifecycleStage />
        <FeaturesSection />
        <AISection />
        <GoalsSection />
        <SharedSection />
        <DownloadSection />
      </main>
      <Footer />
    </>
  )
}

