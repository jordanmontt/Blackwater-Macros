import type { Metadata } from "next";
import { ActivityIcon, BookOpenIcon, DumbbellIcon, FlameIcon, ScaleIcon, TrendingUpIcon, UtensilsCrossedIcon, SigmaIcon } from "lucide-react";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { ClosePageButton } from "@/components/close-page-button";
import { t } from "@/i18n";

export const metadata: Metadata = {
  title: `${t.metodologia.title} · ${t.appName}`,
};

export default function MetodologiaPage() {
  return (
    <main className="mx-auto w-full max-w-2xl space-y-4 px-4 pt-4 pb-12 md:pt-6">
      <header className="flex items-center justify-between gap-2">
        <h1 className="text-lg font-semibold">{t.metodologia.title}</h1>
        <ClosePageButton />
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
          <span className="text-muted-foreground">{t.metodologia.movingAvgWindow}</span>
        </FormulaBlock>
        <p>{t.metodologia.movingAvgBody}</p>
      </MethodologyCard>

      <MethodologyCard icon={<TrendingUpIcon />} title={t.metodologia.rateTitle}>
        <p className="font-medium">{t.metodologia.rateFormulaLabel}</p>
        <FormulaBlock>
          β = Σ(x<sub>i</sub> − x̄)(y<sub>i</sub> − ȳ) / Σ(x<sub>i</sub> − x̄)<sup>2</sup>
          <br />
          {t.metodologia.rateFormula}
        </FormulaBlock>
        <p>{t.metodologia.rateBody}</p>
      </MethodologyCard>

      <MethodologyCard icon={<UtensilsCrossedIcon />} title={t.metodologia.nutritionTitle}>
        <p>{t.metodologia.nutritionBody}</p>
      </MethodologyCard>

      <MethodologyCard icon={<DumbbellIcon />} title={t.metodologia.proteinRecTitle}>
        <p>{t.metodologia.proteinRecBody}</p>
        <p>{t.metodologia.proteinRecRanges}</p>
        <p>{t.metodologia.proteinRecFfm}</p>
      </MethodologyCard>

      <MethodologyCard icon={<FlameIcon />} title={t.metodologia.tdeeTitle}>
        <p>{t.metodologia.tdeeBody}</p>
        <FormulaLines text={t.metodologia.tdeeFormula} />
        <p>{t.metodologia.tdeeActivity}</p>
        <p>{t.metodologia.tdeeWhyNotScale}</p>
        <p>{t.metodologia.tdeeGoals}</p>
      </MethodologyCard>

      <MethodologyCard icon={<ActivityIcon />} title={t.metodologia.measuredTitle}>
        <p>{t.metodologia.measuredBody}</p>
        <FormulaLines text={t.metodologia.measuredFormula} />
        <p>{t.metodologia.measuredData}</p>
        <p>{t.metodologia.measuredUncertainty}</p>
        <p>{t.metodologia.measuredLimits}</p>
      </MethodologyCard>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="flex items-center gap-2 text-base">
            <BookOpenIcon className="size-4" /> {t.metodologia.referencesTitle}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <ol className="list-decimal space-y-2 pl-5 text-sm text-muted-foreground">
            {REFERENCES.map((ref) => (
              <li key={ref.title}>
                {ref.authors}. {ref.title}. <i>{ref.source}</i>. {ref.details}
                {ref.doi ? (
                  <>
                    {" "}
                    <a
                      href={`https://doi.org/${ref.doi}`}
                      className="underline hover:text-foreground"
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      doi:{ref.doi}
                    </a>
                  </>
                ) : null}
              </li>
            ))}
          </ol>
        </CardContent>
      </Card>
    </main>
  );
}

/**
 * Cited in the text above. Keep in sync with `ReferencesCard` in the Android
 * `MethodologyScreen.kt`. DOIs checked against Crossref.
 */
const REFERENCES: { authors: string; title: string; source: string; details: string; doi?: string }[] = [
  {
    authors: "Zheng Y, Klem ML, Sereika SM, Danford CA, Ewing LJ, Burke LE",
    title: "Self-weighing in weight management: a systematic literature review",
    source: "Obesity",
    details: "2015;23(2):256–265.",
    doi: "10.1002/oby.20946",
  },
  {
    authors: "Walker J",
    title: "The Hacker's Diet: how to lose weight and hair through stress and poor nutrition",
    source: "fourmilab.ch",
    details: "2005.",
  },
  {
    authors: "Montgomery DC, Peck EA, Vining GG",
    title: "Introduction to Linear Regression Analysis",
    source: "Wiley",
    details: "6.ª ed. 2021.",
  },
  {
    authors: "Mifflin MD, St Jeor ST, Hill LA, Scott BJ, Daugherty SA, Koh YO",
    title: "A new predictive equation for resting energy expenditure in healthy individuals",
    source: "Am J Clin Nutr",
    details: "1990;51(2):241–247.",
    doi: "10.1093/ajcn/51.2.241",
  },
  {
    authors: "Frankenfield D, Roth-Yousey L, Compher C",
    title:
      "Comparison of predictive equations for resting metabolic rate in healthy nonobese and obese adults: a systematic review",
    source: "J Am Diet Assoc",
    details: "2005;105(5):775–789.",
    doi: "10.1016/j.jada.2005.02.005",
  },
  {
    authors: "FAO/WHO/UNU",
    title: "Human energy requirements: report of a joint FAO/WHO/UNU expert consultation",
    source: "FAO Food and Nutrition Technical Report Series 1",
    details: "Roma: FAO; 2004.",
  },
  {
    authors: "Herrmann SD, Willis EA, Ainsworth BE, et al.",
    title: "2024 Adult Compendium of Physical Activities: a third update of the energy costs of human activities",
    source: "J Sport Health Sci",
    details: "2024;13(1):6–12.",
    doi: "10.1016/j.jshs.2023.10.010",
  },
  {
    authors: "Prince SA, Adamo KB, Hamel ME, Hardt J, Connor Gorber S, Tremblay M",
    title:
      "A comparison of direct versus self-report measures for assessing physical activity in adults: a systematic review",
    source: "Int J Behav Nutr Phys Act",
    details: "2008;5:56.",
    doi: "10.1186/1479-5868-5-56",
  },
  {
    authors: "Hall KD",
    title: "What is the required energy deficit per unit weight loss?",
    source: "Int J Obes",
    details: "2008;32(3):573–576.",
    doi: "10.1038/sj.ijo.0803720",
  },
  {
    authors: "Jäger R, Kerksick CM, Campbell BI, et al.",
    title: "International Society of Sports Nutrition Position Stand: protein and exercise",
    source: "J Int Soc Sports Nutr",
    details: "2017;14:20.",
    doi: "10.1186/s12970-017-0177-8",
  },
  {
    authors: "Morton RW, Murphy KT, McKellar SR, et al.",
    title:
      "A systematic review, meta-analysis and meta-regression of the effect of protein supplementation on resistance training-induced gains in muscle mass and strength in healthy adults",
    source: "Br J Sports Med",
    details: "2018;52(6):376–384.",
    doi: "10.1136/bjsports-2017-097608",
  },
  {
    authors: "Iraki J, Fitschen P, Espinar S, Helms E",
    title: "Nutrition recommendations for bodybuilders in the off-season: a narrative review",
    source: "Sports",
    details: "2019;7(7):154.",
    doi: "10.3390/sports7070154",
  },
  {
    authors: "Helms ER, Aragon AA, Fitschen PJ",
    title: "Evidence-based recommendations for natural bodybuilding contest preparation: nutrition and supplementation",
    source: "J Int Soc Sports Nutr",
    details: "2014;11:20.",
    doi: "10.1186/1550-2783-11-20",
  },
  {
    authors: "Helms ER, Zinn C, Rowlands DS, Brown SR",
    title:
      "A systematic review of dietary protein during caloric restriction in resistance trained lean athletes: a case for higher intakes",
    source: "Int J Sport Nutr Exerc Metab",
    details: "2014;24(2):127–138.",
    doi: "10.1123/ijsnem.2013-0054",
  },
  {
    authors: "Kokura Y, Ueshima J, Saino Y, Maeda K",
    title:
      "Enhanced protein intake on maintaining muscle mass, strength, and physical function in adults with overweight/obesity: a systematic review and meta-analysis",
    source: "Clin Nutr ESPEN",
    details: "2024;63:417–426.",
    doi: "10.1016/j.clnesp.2024.06.030",
  },
  {
    authors: "Nunes EA, Colenso-Semple L, McKellar SR, et al.",
    title: "Systematic review and meta-analysis of protein intake to support muscle mass and function in healthy adults",
    source: "J Cachexia Sarcopenia Muscle",
    details: "2022;13(2):795–810.",
    doi: "10.1002/jcsm.12922",
  },
];

function FormulaLines({ text }: { text: string }) {
  return (
    <FormulaBlock>
      {text.split("\n").map((line, i) => (
        <span key={i}>
          {line}
          <br />
        </span>
      ))}
    </FormulaBlock>
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
