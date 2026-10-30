"use client";

import { ActivityIcon, ArrowLeftRightIcon, ArrowUpDownIcon, BookOpenIcon, DatabaseIcon, DumbbellIcon, FlameIcon, HistoryIcon, ScaleIcon, SigmaIcon, SlidersHorizontalIcon, SparklesIcon, TrendingUpIcon, UtensilsCrossedIcon } from "lucide-react";
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { ClosePageButton } from "@/components/close-page-button";
import { t } from "@/i18n";

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
        <p>{t.metodologia.proteinRecReference}</p>
        <p>{t.metodologia.proteinRecAbove}</p>
      </MethodologyCard>

      <MethodologyCard icon={<FlameIcon />} title={t.metodologia.tdeeTitle}>
        <p>{t.metodologia.tdeeBody}</p>
        <FormulaLines text={t.metodologia.tdeeFormula} />
        <p>{t.metodologia.tdeeActivity}</p>
        <p>{t.metodologia.tdeeWhyNotScale}</p>
        <p>{t.metodologia.tdeeGoals}</p>
      </MethodologyCard>

      <MethodologyCard id="recomposition" icon={<ArrowUpDownIcon />} title={t.metodologia.recompTitle}>
        <p>{t.metodologia.recompBody}</p>
        <p>{t.metodologia.recompCut}</p>
        <p>{t.metodologia.recompScale}</p>
      </MethodologyCard>

      <MethodologyCard id="past-days" icon={<HistoryIcon />} title={t.metodologia.pastDaysTitle}>
        <p>{t.metodologia.pastDaysBody}</p>
        <p>{t.metodologia.pastDaysScale}</p>
        <p>{t.metodologia.pastDaysFix}</p>
      </MethodologyCard>

      <MethodologyCard icon={<ActivityIcon />} title={t.metodologia.measuredTitle}>
        <p>{t.metodologia.measuredBody}</p>
        <FormulaLines text={t.metodologia.measuredFormula} />
        <p>{t.metodologia.measuredData}</p>
        <p>{t.metodologia.measuredUncertainty}</p>
        <p>{t.metodologia.measuredMorning}</p>
        <p>{t.metodologia.measuredLimits}</p>
      </MethodologyCard>

      {/* Linked from the «Gasto usado para tu objetivo» line (/metodologia#adaptive). */}
      <MethodologyCard id="adaptive" icon={<SlidersHorizontalIcon />} title={t.metodologia.adaptiveTitle}>
        <p>{t.metodologia.adaptiveBody}</p>
        <FormulaLines text={t.metodologia.adaptiveFormula} />
        <p>{t.metodologia.adaptiveExample}</p>
        <p>{t.metodologia.adaptiveWhy}</p>
        <p>{t.metodologia.adaptiveWeekly}</p>
      </MethodologyCard>

      {/* Linked from the «Why?» of the expenditure check (/metodologia#check). */}
      <MethodologyCard id="check" icon={<ArrowLeftRightIcon />} title={t.metodologia.checkTitle}>
        <p>{t.metodologia.checkBody}</p>
        <FormulaLines text={t.metodologia.checkFormula} />
        <p>{t.metodologia.checkWhy10}</p>
        <p>{t.metodologia.checkWhyStrict}</p>
        <p>{t.metodologia.checkCauses}</p>
        <p>{t.metodologia.checkShown}</p>
      </MethodologyCard>

      <MethodologyCard icon={<SparklesIcon />} title={t.metodologia.aiTitle}>
        {t.metodologia.aiBody.map((paragraph) => (
          <p key={paragraph}>{paragraph}</p>
        ))}
      </MethodologyCard>

      <MethodologyCard icon={<DatabaseIcon />} title={t.metodologia.sourcesTitle}>
        {t.metodologia.sourcesBody.map((paragraph) => (
          <p key={paragraph}>{paragraph}</p>
        ))}
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
  {
    authors: "Barakat C, Pearson J, Escalante G, Campbell B, De Souza EO",
    title: "Body recomposition: can trained individuals build muscle and lose fat at the same time?",
    source: "Strength Cond J",
    details: "2020;42(5):7–21.",
    doi: "10.1519/SSC.0000000000000584",
  },
  {
    authors: "Antonio J, Ellerbroek A, Silver T, et al.",
    title:
      "A high protein diet (3.4 g/kg/d) combined with a heavy resistance training program improves body composition in healthy trained men and women – a follow-up investigation",
    source: "J Int Soc Sports Nutr",
    details: "2015;12:39.",
    doi: "10.1186/s12970-015-0100-0",
  },
  {
    authors: "Antonio J, Ellerbroek A, Silver T, et al.",
    title: "A high protein diet has no harmful effects: a one-year crossover study in resistance-trained males",
    source: "J Nutr Metab",
    details: "2016;2016:9104792.",
    doi: "10.1155/2016/9104792",
  },
  {
    authors: "Devries MC, Sithamparapillai A, Brimble KS, Banfield L, Morton RW, Phillips SM",
    title:
      "Changes in kidney function do not differ between healthy adults consuming higher- compared with lower- or normal-protein diets: a systematic review and meta-analysis",
    source: "J Nutr",
    details: "2018;148(11):1760–1775.",
    doi: "10.1093/jn/nxy197",
  },
  {
    authors: "Murphy C, Koehler K",
    title: "Energy deficiency impairs resistance training gains in lean mass but not strength: a meta-analysis and meta-regression",
    source: "Scand J Med Sci Sports",
    details: "2022;32(1):125–137.",
    doi: "10.1111/sms.14075",
  },
  {
    authors: "Garthe I, Raastad T, Refsnes PE, Koivisto A, Sundgot-Borgen J",
    title:
      "Effect of two different weight-loss rates on body composition and strength and power-related performance in elite athletes",
    source: "Int J Sport Nutr Exerc Metab",
    details: "2011;21(2):97–104.",
    doi: "10.1123/ijsnem.21.2.97",
  },
  {
    authors: "Gallagher D, Heymsfield SB, Heo M, Jebb SA, Murgatroyd PR, Sakamoto Y",
    title: "Healthy percentage body fat ranges: an approach for developing guidelines based on body mass index",
    source: "Am J Clin Nutr",
    details: "2000;72(3):694–701.",
    doi: "10.1093/ajcn/72.3.694",
  },
  {
    authors: "McClave SA, Taylor BE, Martindale RG, et al.",
    title:
      "Guidelines for the provision and assessment of nutrition support therapy in the adult critically ill patient (SCCM and A.S.P.E.N.)",
    source: "JPEN J Parenter Enteral Nutr",
    details: "2016;40(2):159–211.",
    doi: "10.1177/0148607115621863",
  },
  {
    authors: "Byrne NM, Hills AP, Hunter GR, Weinsier RL, Schutz Y",
    title: "Metabolic equivalent: one size does not fit all",
    source: "J Appl Physiol",
    details: "2005;99(3):1112–1119.",
    doi: "10.1152/japplphysiol.00023.2004",
  },
  {
    authors: "Lichtman SW, Pisarska K, Berman ER, et al.",
    title: "Discrepancy between self-reported and actual caloric intake and exercise in obese subjects",
    source: "N Engl J Med",
    details: "1992;327(27):1893–1898.",
    doi: "10.1056/NEJM199212313272701",
  },
  {
    authors: "Schoeller DA",
    title: "Limitations in the assessment of dietary energy intake by self-report",
    source: "Metabolism",
    details: "1995;44(2 Suppl 2):18–22.",
    doi: "10.1016/0026-0495(95)90204-X",
  },
  {
    authors: "Rosenbaum M, Leibel RL",
    title: "Adaptive thermogenesis in humans",
    source: "Int J Obes",
    details: "2010;34(Suppl 1):S47–S55.",
    doi: "10.1038/ijo.2010.184",
  },
  {
    authors: "Kreitzman SN, Coxon AY, Szaz KF",
    title:
      "Glycogen storage: illusions of easy weight loss, excessive weight regain, and distortions in estimates of body composition",
    source: "Am J Clin Nutr",
    details: "1992;56(1 Suppl):292S–293S.",
    doi: "10.1093/ajcn/56.1.292S",
  },
  {
    authors: "Bajunaid R, Niu C, Hambly C, et al.",
    title:
      "Predictive equation derived from 6,497 doubly labelled water measurements enables the detection of erroneous self-reported energy intake",
    source: "Nat Food",
    details: "2025;6(1):58–71.",
    doi: "10.1038/s43016-024-01089-5",
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
  id,
  icon,
  title,
  children,
}: {
  id?: string;
  icon: React.ReactNode;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <Card id={id} className="scroll-mt-4">
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
