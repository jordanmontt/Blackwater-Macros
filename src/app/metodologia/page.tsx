import type { Metadata } from "next";
import { BookOpenIcon, DumbbellIcon, ScaleIcon, TrendingUpIcon, UtensilsCrossedIcon, CalendarDaysIcon, SigmaIcon } from "lucide-react";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { t } from "@/i18n";

export const metadata: Metadata = {
  title: `${t.metodologia.title} · ${t.appName}`,
};

export default function MetodologiaPage() {
  return (
    <main className="mx-auto w-full max-w-2xl space-y-4 px-4 pt-4 pb-12 md:pt-6">
      <header className="flex items-center justify-between gap-2">
        <h1 className="text-lg font-semibold">{t.metodologia.title}</h1>
      </header>

      <p className="text-sm text-muted-foreground">{t.metodologia.intro}</p>

      <MethodologyCard icon={<ScaleIcon />} title={t.metodologia.scaleVsTrendTitle}>
        <p>{t.metodologia.scaleVsTrendBody}</p>
      </MethodologyCard>

      <MethodologyCard icon={<SigmaIcon />} title={t.metodologia.movingAvgTitle}>
        <p className="font-medium">{t.metodologia.movingAvgFormulaLabel}</p>
        <FormulaBlock>
          MA(d) = (w<sub>1</sub> + w<sub>2</sub> + … + w<sub>n</sub>) / n
          <br />
          <span className="text-muted-foreground">
            W(d) = {"{"} entradas con fecha en [d − 6, d] {"}"}
          </span>
        </FormulaBlock>
        <p>{t.metodologia.movingAvgBody}</p>
      </MethodologyCard>

      <MethodologyCard icon={<TrendingUpIcon />} title={t.metodologia.rateTitle}>
        <p className="font-medium">{t.metodologia.rateFormulaLabel}</p>
        <FormulaBlock>
          β = Σ(x<sub>i</sub> − x̄)(y<sub>i</sub> − ȳ) / Σ(x<sub>i</sub> − x̄)<sup>2</sup>
          <br />
          ritmo = β × 7 [kg/semana]
        </FormulaBlock>
        <p>{t.metodologia.rateBody}</p>
      </MethodologyCard>

      <MethodologyCard icon={<CalendarDaysIcon />} title={t.metodologia.weeklyAvgTitle}>
        <p>{t.metodologia.weeklyAvgBody}</p>
      </MethodologyCard>

      <MethodologyCard icon={<UtensilsCrossedIcon />} title={t.metodologia.nutritionTitle}>
        <p>{t.metodologia.nutritionBody}</p>
      </MethodologyCard>

      <MethodologyCard icon={<DumbbellIcon />} title={t.metodologia.proteinRecTitle}>
        <p>{t.metodologia.proteinRecBody}</p>
        <p>{t.metodologia.proteinRecRanges}</p>
        <p>{t.metodologia.proteinRecFfm}</p>
      </MethodologyCard>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="flex items-center gap-2 text-base">
            <BookOpenIcon className="size-4" /> {t.metodologia.referencesTitle}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <ol className="list-decimal space-y-2 pl-5 text-sm text-muted-foreground">
            <li>
              Zheng Y, Burke LE, Danford CA, Ewing LJ, Terry MA, Sereika SM. «Self-weighing in
              weight management: a systematic literature review». <i>Obesity Reviews</i>.
              2015;16(2):124–139.
            </li>
            <li>
              Walker J. <i>The Hacker&apos;s Diet: How to lose weight and hair through stress and
              poor nutrition</i>. 3.ª ed. 2005. Disponible en línea (fourmilab.ch).
            </li>
            <li>
              Montgomery DC, Peck EA, Vining GG. <i>Introduction to Linear Regression Analysis</i>.
              6.ª ed. Hoboken (NJ): Wiley; 2021.
            </li>
            <li>
              Morton RW et al. A systematic review, meta-analysis and meta-regression of the effect
              of protein supplementation on resistance training-induced gains in muscle mass and
              strength. <i>Br J Sports Med</i>. 2018;52:376–384.{" "}
              <a
                href="https://doi.org/10.1136/bjsports-2017-097608"
                className="underline hover:text-foreground"
                target="_blank"
                rel="noopener noreferrer"
              >
                doi:10.1136/bjsports-2017-097608
              </a>
            </li>
            <li>
              Nunes EA et al. Systematic review and meta-analysis of protein intake to support muscle
              mass and function in healthy adults. <i>J Cachexia Sarcopenia Muscle</i>.
              2022;13:795–810.{" "}
              <a
                href="https://doi.org/10.1002/jcsm.12924"
                className="underline hover:text-foreground"
                target="_blank"
                rel="noopener noreferrer"
              >
                doi:10.1002/jcsm.12924
              </a>
            </li>
            <li>
              Kokura Y et al. Enhanced protein intake on maintaining muscle mass, strength, and
              physical function in adults with overweight/obesity. <i>Clin Nutr ESPEN</i>. 2024.{" "}
              <a
                href="https://doi.org/10.1016/j.clnesp.2024.01.003"
                className="underline hover:text-foreground"
                target="_blank"
                rel="noopener noreferrer"
              >
                doi:10.1016/j.clnesp.2024.01.003
              </a>
            </li>
            <li>
              Helms ER et al. A systematic review of dietary protein during caloric restriction in
              resistance trained lean athletes: a case for higher intakes. <i>
                Int J Sport Nutr Exerc Metab
              </i>
              . 2014;24(2):127–138.{" "}
              <a
                href="https://doi.org/10.1123/ijsnem.2013-0054"
                className="underline hover:text-foreground"
                target="_blank"
                rel="noopener noreferrer"
              >
                doi:10.1123/ijsnem.2013-0054
              </a>
            </li>
            <li>
              Jäger R et al. International Society of Sports Nutrition Position Stand: protein and
              exercise. <i>J Int Soc Sports Nutr</i>. 2017;14:20.{" "}
              <a
                href="https://doi.org/10.1186/s12970-017-0177-8"
                className="underline hover:text-foreground"
                target="_blank"
                rel="noopener noreferrer"
              >
                doi:10.1186/s12970-017-0177-8
              </a>
            </li>
          </ol>
        </CardContent>
      </Card>
    </main>
  );
}

function MethodologyCard({
  icon,
  title,
  children,
}: {
  icon: React.ReactNode;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-base">
          {icon} {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 text-sm leading-relaxed text-muted-foreground [&_p]:text-muted-foreground">
        {children}
      </CardContent>
    </Card>
  );
}

function FormulaBlock({ children }: { children: React.ReactNode }) {
  return (
    <div className="rounded-lg border bg-muted/40 px-3 py-2.5 font-mono text-xs leading-relaxed text-foreground">
      {children}
    </div>
  );
}
